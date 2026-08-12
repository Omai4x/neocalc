package com.omai.neocalc.alerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.omai.neocalc.R
import com.omai.neocalc.convert.Currencies

/**
 * Setting and listing rate alerts for the pair currently on screen.
 *
 * Deliberately sits below the fold: it is a power feature, and the converter has
 * to stay a converter for the people who just want a number.
 */
@Composable
fun AlertsPanel(
    from: String,
    to: String,
    currentRate: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var alerts by remember { mutableStateOf(RateAlerts.all(context)) }
    var target by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(AlertDirection.Above) }
    var needsPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED,
        )
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> needsPermission = !granted }

    fun create() {
        val value = target.replace(',', '.').toDoubleOrNull() ?: return
        alerts = RateAlerts.add(context, from, to, value, direction)
        target = ""
        // Asking only once the user has actually created an alert is the moment
        // the permission makes sense to them.
        if (needsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = scheme.primary,
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    text = "  " + stringResource(R.string.alerts_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    AlertDirection.Above to R.string.alerts_above,
                    AlertDirection.Below to R.string.alerts_below,
                ).forEach { (option, label) ->
                    val active = option == direction
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) scheme.primary else scheme.surfaceVariant)
                            .clickable { direction = option }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { text -> target = text.filter { it.isDigit() || it == '.' } },
                    label = { Text("$from to $to") },
                    // Prefilled with today's rate as a placeholder, which is the
                    // number people adjust from rather than invent.
                    placeholder = {
                        Text(currentRate?.let { String.format("%.4f", it) } ?: "0.0000")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = ::create,
                    enabled = target.toDoubleOrNull() != null,
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
                ) {
                    Text(
                        text = stringResource(R.string.alerts_add),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }

            if (needsPermission && alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.alerts_permission),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            if (alerts.isEmpty()) {
                Text(
                    text = stringResource(R.string.alerts_empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                alerts.forEach { alert ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = Currencies.info(alert.from).flag + " " + alert.describe(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove this alert",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { alerts = RateAlerts.remove(context, alert) }
                                .padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}
