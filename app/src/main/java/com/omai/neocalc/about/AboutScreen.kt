package com.omai.neocalc.about

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import com.omai.neocalc.history.HistoryEntry
import com.omai.neocalc.backup.Backup
import com.omai.neocalc.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Download
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omai.neocalc.ui.LocalWindowSize
import com.omai.neocalc.ui.ResponsiveColumn

/** Which document is open. Null means the index. */
private enum class Doc { Privacy, Terms, Releases }

/**
 * About, and the two documents every app is expected to be able to show without
 * a network connection.
 */
@Composable
fun AboutScreen(
    versionName: String,
    onClose: () -> Unit,
    onReplayTour: () -> Unit,
    modifier: Modifier = Modifier,
    history: List<HistoryEntry> = emptyList(),
    onHistoryImported: (List<HistoryEntry>) -> Unit = {},
) {
    var open by rememberSaveable { mutableStateOf<Doc?>(null) }
    val scheme = MaterialTheme.colorScheme
    val window = LocalWindowSize.current

    BackHandler { if (open != null) open = null else onClose() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Surface(
                onClick = { if (open != null) open = null else onClose() },
                shape = CircleShape,
                color = scheme.surfaceVariant,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = when (open) {
                    Doc.Privacy -> "Privacy policy"
                    Doc.Terms -> "Terms and conditions"
                    Doc.Releases -> "What's new"
                    null -> "About NeoCalc"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = window.gutter),
        ) {
            ResponsiveColumn {
                when (open) {
                    null -> Index(
                        versionName = versionName,
                        onOpen = { open = it },
                        onReplayTour = onReplayTour,
                        history = history,
                        onHistoryImported = onHistoryImported,
                    )

                    Doc.Privacy -> Document(
                        subtitle = "Effective ${Legal.EFFECTIVE}",
                        sections = Legal.PRIVACY,
                    )

                    Doc.Terms -> Document(
                        subtitle = "Effective ${Legal.EFFECTIVE}",
                        sections = Legal.TERMS,
                    )

                    Doc.Releases -> Releases()
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun Index(
    versionName: String,
    onOpen: (Doc) -> Unit,
    onReplayTour: () -> Unit,
    history: List<HistoryEntry>,
    onHistoryImported: (List<HistoryEntry>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // Outcomes are held as data and rendered below with stringResource, rather
    // than formatted into a String here. Reading resources off the Context in a
    // callback would pin the message to whatever locale was current at the time.
    var outcome by remember { mutableStateOf<Int?>(null) }
    var imported by remember { mutableStateOf<Backup.Imported?>(null) }

    // The system picker owns the file, so the app never needs storage
    // permission and the user always knows exactly where their data went.
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Backup.MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = Backup.write(context, uri, Backup.export(context, history))
        outcome = if (ok) R.string.backup_exported else R.string.backup_failed
        imported = null
    }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Backup.CSV_MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = Backup.write(context, uri, Backup.historyCsv(history))
        outcome = if (ok) R.string.backup_exported else R.string.backup_failed
        imported = null
    }

    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = Backup.read(context, uri)
        if (text == null) {
            outcome = R.string.backup_failed
            imported = null
            return@rememberLauncherForActivityResult
        }
        Backup.import(context, text).fold(
            onSuccess = { result ->
                onHistoryImported(result.history)
                imported = result
                outcome = null
            },
            onFailure = {
                imported = null
                outcome = R.string.backup_failed
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "NeoCalc",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A calculator that also does money. No account, no tracking, " +
                    "no ads, and it works with the network off.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }

    listOf(
        Triple("What's new", "Everything in this release", Doc.Releases),
        Triple("Privacy policy", "What the app does and does not collect", Doc.Privacy),
        Triple("Terms and conditions", "Rates are indicative, not dealing prices", Doc.Terms),
    ).forEach { (title, subtitle, doc) ->
        LinkRow(title = title, subtitle = subtitle, icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight) {
            onOpen(doc)
        }
    }

    LinkRow(
        title = "Show the welcome tour again",
        subtitle = "Replay the five-page introduction",
        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        onClick = onReplayTour,
    )

    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.backup_title).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurfaceVariant,
    )
    LinkRow(
        title = stringResource(R.string.backup_export),
        subtitle = stringResource(R.string.backup_export_detail),
        icon = Icons.Rounded.Upload,
        onClick = { exportBackup.launch(Backup.suggestedName("neocalc-backup", "json")) },
    )
    LinkRow(
        title = stringResource(R.string.backup_import),
        subtitle = stringResource(R.string.backup_import_detail),
        icon = Icons.Rounded.Download,
        onClick = { importBackup.launch(arrayOf(Backup.MIME, "*/*")) },
    )
    LinkRow(
        title = stringResource(R.string.backup_csv),
        subtitle = stringResource(R.string.backup_csv_detail),
        icon = Icons.Rounded.TableChart,
        onClick = { exportCsv.launch(Backup.suggestedName("neocalc-history", "csv")) },
    )

    outcome?.let { resource ->
        Text(
            text = stringResource(resource),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.primary,
        )
    }
    imported?.let { result ->
        Text(
            text = stringResource(
                R.string.backup_imported,
                result.favourites,
                result.customUnits,
                result.alerts,
                result.history.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.primary,
        )
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Document(subtitle: String, sections: List<Legal.Section>) {
    val scheme = MaterialTheme.colorScheme
    Spacer(Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant,
    )
    sections.forEach { section ->
        Column {
            Text(
                text = section.heading,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = section.body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
private fun Releases() {
    val scheme = MaterialTheme.colorScheme
    ReleaseNotes.ALL.forEach { release ->
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = release.version,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = release.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = release.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                release.changes.forEach { change ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(6.dp)
                                .background(scheme.primary, CircleShape),
                        )
                        Text(
                            text = change,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
