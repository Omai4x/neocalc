package com.omai.neocalc.alerts

import androidx.core.content.ContextCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omai.neocalc.MainActivity
import com.omai.neocalc.R
import com.omai.neocalc.convert.CurrencyApi
import com.omai.neocalc.convert.RateCache
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Which way a rate has to move for the alert to fire. */
enum class AlertDirection { Above, Below }

/**
 * "Tell me when USD to NGN goes above 1600."
 *
 * [lastFired] is why an alert does not nag: once it has gone off it stays quiet
 * until the rate crosses back and then crosses again, rather than notifying on
 * every single check for as long as the condition holds.
 */
data class RateAlert(
    val id: String,
    val from: String,
    val to: String,
    val target: Double,
    val direction: AlertDirection,
    val lastFired: Long = 0L,
    val armed: Boolean = true,
) {
    fun triggeredBy(rate: Double): Boolean = when (direction) {
        AlertDirection.Above -> rate >= target
        AlertDirection.Below -> rate <= target
    }

    fun describe(): String = when (direction) {
        AlertDirection.Above -> "$from to $to above ${format(target)}"
        AlertDirection.Below -> "$from to $to below ${format(target)}"
    }

    private fun format(value: Double) = String.format(Locale.getDefault(), "%,.4f", value)
}

object RateAlerts {

    private const val PREFS = "neocalc.alerts"
    private const val KEY = "alerts"
    private const val WORK = "neocalc.alerts.check"
    const val CHANNEL = "rate_alerts"

    /** More than a handful of alerts is a trading app, which this is not. */
    const val MAX = 8

    fun all(context: Context): List<RateAlert> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun add(
        context: Context,
        from: String,
        to: String,
        target: Double,
        direction: AlertDirection,
    ): List<RateAlert> {
        if (!target.isFinite() || target <= 0.0) return all(context)
        val alert = RateAlert(
            id = "$from-$to-$direction-${target}",
            from = from,
            to = to,
            target = target,
            direction = direction,
        )
        val updated = (all(context).filterNot { it.id == alert.id } + alert).takeLast(MAX)
        save(context, updated)
        schedule(context)
        return updated
    }

    fun remove(context: Context, alert: RateAlert): List<RateAlert> {
        val updated = all(context).filterNot { it.id == alert.id }
        save(context, updated)
        if (updated.isEmpty()) cancel(context)
        return updated
    }

    internal fun save(context: Context, alerts: List<RateAlert>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(alerts))
            .apply()
    }

    /**
     * Checks a few times a day, only on a network, and never on a schedule
     * tighter than WorkManager's 15-minute floor. Reference rates move once a
     * working day; polling harder would spend battery to learn nothing.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RateAlertWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.alerts_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.alerts_channel_description)
            },
        )
    }

    internal fun encode(alerts: List<RateAlert>): String = JSONArray().apply {
        alerts.forEach { alert ->
            put(
                JSONObject().apply {
                    put("id", alert.id)
                    put("from", alert.from)
                    put("to", alert.to)
                    put("target", alert.target)
                    put("direction", alert.direction.name)
                    put("lastFired", alert.lastFired)
                    put("armed", alert.armed)
                },
            )
        }
    }.toString()

    internal fun decode(json: String?): List<RateAlert> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val direction = runCatching {
                    AlertDirection.valueOf(item.getString("direction"))
                }.getOrNull() ?: return@mapNotNull null
                RateAlert(
                    id = item.getString("id"),
                    from = item.getString("from"),
                    to = item.getString("to"),
                    target = item.getDouble("target"),
                    direction = direction,
                    lastFired = item.optLong("lastFired"),
                    armed = item.optBoolean("armed", true),
                )
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * The periodic check. Groups alerts by base currency so a set of alerts on the
 * same base costs one request, not one per alert.
 */
class RateAlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val alerts = RateAlerts.all(applicationContext)
        if (alerts.isEmpty()) return Result.success()

        var updated = alerts
        alerts.groupBy { it.from }.forEach { (base, group) ->
            val table = CurrencyApi.fetchRates(base).getOrNull() ?: return@forEach
            RateCache.save(applicationContext, table)

            group.forEach { alert ->
                val rate = table.rateFor(alert.to) ?: return@forEach
                val hit = alert.triggeredBy(rate)
                updated = updated.map { candidate ->
                    when {
                        candidate.id != alert.id -> candidate
                        // Fires once on the crossing, then disarms until the
                        // rate comes back the other side of the target.
                        hit && candidate.armed -> {
                            notify(candidate, rate)
                            candidate.copy(armed = false, lastFired = System.currentTimeMillis())
                        }

                        !hit -> candidate.copy(armed = true)
                        else -> candidate
                    }
                }
            }
        }
        RateAlerts.save(applicationContext, updated)
        return Result.success()
    }

    private fun notify(alert: RateAlert, rate: Double) {
        RateAlerts.ensureChannel(applicationContext)
        val manager = NotificationManagerCompat.from(applicationContext)
        // Posting without permission throws on API 33+; the alert simply does
        // not surface, which is the correct outcome for a denied permission.
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = PendingIntent.getActivity(
            applicationContext,
            alert.id.hashCode(),
            android.content.Intent(applicationContext, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, RateAlerts.CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(
                applicationContext.getString(
                    R.string.alerts_fired_title,
                    alert.from,
                    alert.to,
                ),
            )
            .setContentText(
                applicationContext.getString(
                    R.string.alerts_fired_body,
                    String.format(Locale.getDefault(), "%,.4f", rate),
                    alert.describe(),
                ),
            )
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(alert.id.hashCode(), notification) }
    }
}
