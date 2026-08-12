package com.omai.neocalc.backup

import android.content.Context
import android.net.Uri
import com.omai.neocalc.alerts.RateAlerts
import com.omai.neocalc.convert.ConvertPrefs
import com.omai.neocalc.convert.CustomUnits
import com.omai.neocalc.history.HistoryEntry
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explicit export and import.
 *
 * Cloud backup is switched off for this app because the privacy policy promises
 * the data stays on the device. That promise is only reasonable if the user has
 * some other way to move it, so this is that way: a file they choose, written
 * where they say, readable by them.
 *
 * The format is plain JSON with a version, so an older build reading a newer
 * file fails cleanly rather than half-importing it.
 */
object Backup {

    const val VERSION = 1
    const val MIME = "application/json"
    const val CSV_MIME = "text/csv"

    fun suggestedName(prefix: String, extension: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "$prefix-$stamp.$extension"
    }

    /** Everything worth carrying to a new phone, as one document. */
    fun export(context: Context, history: List<HistoryEntry>): String {
        val (from, to) = ConvertPrefs.pair(context)
        return JSONObject().apply {
            put("version", VERSION)
            put("exported", System.currentTimeMillis())
            put("pairFrom", from)
            put("pairTo", to)
            put("favourites", JSONArray(ConvertPrefs.favourites(context)))
            put("recents", JSONArray(ConvertPrefs.recents(context)))
            put("customUnits", JSONArray(CustomUnits.encode(CustomUnits.all(context))))
            put("alerts", JSONArray(RateAlerts.encode(RateAlerts.all(context))))
            put(
                "history",
                JSONArray().apply {
                    history.forEach { entry ->
                        put(
                            JSONObject().apply {
                                put("expression", entry.expression)
                                put("result", entry.result)
                            },
                        )
                    }
                },
            )
        }.toString(2)
    }

    /** What an import found, so the UI can say what actually changed. */
    data class Imported(
        val favourites: Int,
        val customUnits: Int,
        val alerts: Int,
        val history: List<HistoryEntry>,
    )

    /**
     * Restores what it can and reports the rest. A file from a future version is
     * rejected outright: importing half of it would be worse than importing none.
     */
    fun import(context: Context, json: String): Result<Imported> = runCatching {
        val root = JSONObject(json)
        val version = root.optInt("version")
        require(version in 1..VERSION) { "Unsupported backup version $version" }

        root.optJSONArray("favourites")?.let { array ->
            val codes = (0 until array.length()).map { array.getString(it) }
            // Written through the same door the UI uses, so the caps and the
            // ordering rules apply to an imported list exactly as to a typed one.
            ConvertPrefs.replaceFavourites(context, codes)
        }
        root.optJSONArray("recents")?.let { array ->
            (0 until array.length()).reversed().forEach {
                ConvertPrefs.recordRecent(context, array.getString(it))
            }
        }
        val units = root.optJSONArray("customUnits")?.let { array ->
            val decoded = CustomUnits.decode(array.optString(0))
            CustomUnits.replaceAll(context, decoded)
            decoded.size
        } ?: 0
        val alerts = root.optJSONArray("alerts")?.let { array ->
            val decoded = RateAlerts.decode(array.optString(0))
            RateAlerts.save(context, decoded)
            if (decoded.isNotEmpty()) RateAlerts.schedule(context)
            decoded.size
        } ?: 0

        val pairFrom = root.optString("pairFrom").takeIf { it.length == 3 }
        val pairTo = root.optString("pairTo").takeIf { it.length == 3 }
        if (pairFrom != null && pairTo != null) ConvertPrefs.savePair(context, pairFrom, pairTo)

        val history = root.optJSONArray("history")?.let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                HistoryEntry(item.getString("expression"), item.getString("result"))
            }
        }.orEmpty()

        Imported(
            favourites = ConvertPrefs.favourites(context).size,
            customUnits = units,
            alerts = alerts,
            history = history,
        )
    }

    /**
     * The history tape as CSV, for a spreadsheet rather than for re-import.
     * Fields are quoted and inner quotes doubled, which is the whole of RFC 4180
     * that matters here.
     */
    fun historyCsv(history: List<HistoryEntry>): String = buildString {
        appendLine("expression,result")
        history.forEach { entry ->
            append(quote(entry.expression))
            append(',')
            appendLine(quote(entry.result))
        }
    }

    private fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

    fun write(context: Context, target: Uri, content: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(target)?.use { stream ->
            stream.write(content.toByteArray())
        } != null
    }.getOrDefault(false)

    fun read(context: Context, source: Uri): String? = runCatching {
        context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
    }.getOrNull()
}
