package com.omai.neocalc.history

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** One completed calculation, newest first in the tape. */
data class HistoryEntry(
    val expression: String,
    val result: String,
)

/** Keeps the tape bounded so a long session can't grow the saved state forever. */
const val MAX_HISTORY = 100

/**
 * Entries are flattened to a plain string list: [Saver] output has to survive a
 * Bundle round trip, and a list of strings always can.
 */
private const val FIELD_SEPARATOR = "\u001F"

val HistoryListSaver: Saver<SnapshotStateList<HistoryEntry>, Any> = listSaver(
    save = { entries -> entries.map { "${it.expression}$FIELD_SEPARATOR${it.result}" } },
    restore = { saved ->
        saved.filterIsInstance<String>()
            .map { line ->
                val parts = line.split(FIELD_SEPARATOR)
                HistoryEntry(
                    expression = parts.getOrElse(0) { "" },
                    result = parts.getOrElse(1) { "" },
                )
            }
            .toMutableStateList()
    },
)
