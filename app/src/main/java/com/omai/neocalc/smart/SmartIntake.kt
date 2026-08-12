package com.omai.neocalc.smart

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.omai.neocalc.MainActivity
import com.omai.neocalc.convert.ClipboardAmount
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Everything that hands the app a piece of text from outside it: the share
 * sheet, the Quick Settings tile, and a photo of a price tag.
 *
 * They all end in the same place - [NaturalInput] turns the text into an intent
 * and MainActivity acts on it - so adding a new entry point is a manifest entry
 * and nothing more.
 */
object SmartIntake {

    /** Extra carrying text handed in from outside, for MainActivity to read. */
    const val EXTRA_TEXT = "com.omai.neocalc.SHARED_TEXT"

    /** Builds the intent that opens the app on whatever [text] turns out to be. */
    fun intentFor(context: Context, text: String?): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_TEXT, text)
        }

    /**
     * Pulls the most likely price out of recognised text.
     *
     * A photographed receipt is a wall of numbers; the useful one is almost
     * always the largest, so candidates are ranked by value rather than by
     * position, which would pick up a line number or a date.
     */
    fun bestAmountIn(lines: List<String>): String? = lines
        .asSequence()
        .mapNotNull { line -> ClipboardAmount.parse(line.trim()) }
        .maxByOrNull { it.amount }
        ?.let { detected ->
            if (detected.code != null) "${detected.amount} ${detected.code}" else "${detected.amount}"
        }

    /**
     * Runs on-device text recognition over [image]. Bundled ML Kit, so there is
     * no model download and no network call - the photo never leaves the device.
     */
    suspend fun readText(context: Context, image: Uri): List<String> = recognise {
        InputImage.fromFilePath(context, image)
    }

    suspend fun readText(bitmap: Bitmap): List<String> = recognise {
        InputImage.fromBitmap(bitmap, 0)
    }

    private suspend fun recognise(source: () -> InputImage): List<String> =
        runCatching {
            val image = source()
            val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            suspendCoroutine { continuation ->
                recogniser.process(image)
                    .addOnSuccessListener { result ->
                        continuation.resume(result.textBlocks.flatMap { block -> block.lines.map { it.text } })
                    }
                    .addOnFailureListener { continuation.resume(emptyList()) }
            }
        }.getOrDefault(emptyList())
}

/**
 * The share-sheet entry point. Selecting text anywhere on the device and sharing
 * it here converts it, which is the fastest path from "a price on a web page" to
 * "what that is in my money".
 *
 * It is a trampoline with no UI of its own: it reads the text, starts the main
 * activity with it, and finishes before a frame is ever drawn.
 */
class ShareReceiverActivity : android.app.Activity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

            else -> null
        }
        startActivity(SmartIntake.intentFor(this, text))
        finish()
    }
}

/**
 * A Quick Settings tile that opens straight into the converter.
 *
 * TileService arrived in API 24 and minSdk here is 23, so the class is gated;
 * on older devices the manifest entry is simply never bound.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ConvertTileService : TileService() {

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = SmartIntake.intentFor(this, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From API 34 the tile must hand over a PendingIntent rather than
            // launching directly, or the system refuses to collapse the shade.
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
