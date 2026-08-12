package com.omai.neocalc.smart

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Close
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.omai.neocalc.R
import kotlinx.coroutines.launch

/**
 * The one field that understands sentences, and the camera button next to it.
 *
 * Both produce the same thing - a piece of text - and both hand it to
 * [NaturalInput], so a typed phrase, a shared web page and a photographed price
 * tag all take exactly the same path through the app.
 */
@Composable
fun SmartBar(
    onUnderstood: (Understood) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    // Resolved during composition so a locale change re-resolves them; reading
    // them off the Context inside a callback would freeze the first language.
    val notUnderstood = stringResource(R.string.smart_not_understood)
    val scanFailed = stringResource(R.string.scan_failed)

    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    fun submit(input: String) {
        val understood = NaturalInput.parse(input)
        if (understood == null) {
            message = notUnderstood
        } else {
            message = null
            text = ""
            onUnderstood(understood)
        }
    }

    // The cheap camera contract: the system camera app returns a thumbnail
    // bitmap, which is plenty for reading a price and needs no FileProvider,
    // no storage permission and no CameraX.
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            val lines = SmartIntake.readText(bitmap)
            working = false
            val found = SmartIntake.bestAmountIn(lines)
            if (found == null) {
                message = scanFailed
            } else {
                submit(found)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) camera.launch(null) }

    fun scan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) camera.launch(null) else permission.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    message = null
                },
                placeholder = { Text(stringResource(R.string.smart_hint), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = scheme.primary,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit(text) }),
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.surfaceVariant,
                border = BorderStroke(1.dp, scheme.outline),
                modifier = Modifier.size(56.dp),
            ) {
                IconButton(onClick = ::scan, enabled = !working) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = stringResource(R.string.scan_price),
                        tint = scheme.primary,
                    )
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.error,
                modifier = Modifier.padding(start = 14.dp),
            )
        }

        if (working) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.scan_reading),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

/**
 * A one-line summary of what a phrase was taken to mean, shown after it has been
 * applied so the user can see whether they were understood correctly.
 */
@Composable
fun UnderstoodBanner(understood: Understood, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val summary = when (understood) {
        is Understood.Currency ->
            "${understood.amount} ${understood.from}" +
                (understood.to?.let { " to $it" } ?: "")

        is Understood.Units -> "${understood.amount} ${understood.category.label}"
        is Understood.Discount ->
            "${understood.percent}% off ${understood.amount} is ${understood.result}"

        is Understood.Split -> "Split ${understood.amount} ${understood.people} ways"
        is Understood.Arithmetic -> "${understood.expression} = ${understood.value}"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = scheme.primary,
                )
            }
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.smart_dismiss),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}
