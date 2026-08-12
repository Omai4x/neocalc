package com.omai.neocalc.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.omai.neocalc.MainActivity
import com.omai.neocalc.R
import com.omai.neocalc.convert.ConvertPrefs
import com.omai.neocalc.convert.Currencies
import com.omai.neocalc.convert.CurrencyApi
import com.omai.neocalc.convert.RateCache
import com.omai.neocalc.convert.daysSince
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A home-screen view of the pair the user last converted - the fastest possible
 * answer to "what's the rate today", with no app launch at all.
 *
 * Built on RemoteViews rather than Glance so the widget costs the project no new
 * dependency, matching how the rest of the app is put together. It renders from
 * [RateCache], which means it shows something the moment it is placed and keeps
 * working offline.
 */
class RateWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
        refreshRates(context)
    }

    /**
     * Fetches in the background and re-renders when it lands. `goAsync` keeps the
     * broadcast alive past this method, which is the only supported way for a
     * provider to do work that isn't instant.
     */
    private fun refreshRates(context: Context) {
        val (from, _) = ConvertPrefs.pair(context)
        val cached = RateCache.load(context, from)
        if (cached != null && cached.isFresh()) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CurrencyApi.fetchRates(from).onSuccess { table ->
                    RateCache.save(context, table)
                    refresh(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        /** Re-renders every placed widget. Safe to call when none exist. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(context, RateWidget::class.java))
            }.getOrNull() ?: return
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val (from, to) = ConvertPrefs.pair(context)
            val cached = RateCache.load(context, from)
            val rate = cached?.table?.rateFor(to)

            val views = RemoteViews(context.packageName, R.layout.widget_rate).apply {
                setTextViewText(R.id.widget_pair, "${flag(from)} $from → ${flag(to)} $to")
                setTextViewText(
                    R.id.widget_rate,
                    rate?.let { String.format(Locale.getDefault(), "%,.4f", it) }
                        ?: context.getString(R.string.widget_no_rate),
                )
                setTextViewText(R.id.widget_caption, caption(context, cached?.fetchedAt, rate))
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun flag(code: String) = Currencies.info(code).flag

        private fun caption(context: Context, fetchedAt: Long?, rate: Double?): String = when {
            rate == null || fetchedAt == null -> context.getString(R.string.widget_tap_to_open)
            daysSince(fetchedAt) < 1 -> context.getString(R.string.widget_today)
            else -> {
                val days = daysSince(fetchedAt)
                context.resources.getQuantityString(R.plurals.widget_days_ago, days, days)
            }
        }

        /**
         * FLAG_IMMUTABLE is required from API 31 and harmless before it; the
         * widget never needs to fill anything into the intent.
         */
        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
