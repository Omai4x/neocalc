package com.omai.neocalc.backup

import com.omai.neocalc.history.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    @Test
    fun `csv quotes fields and doubles inner quotes`() {
        val csv = Backup.historyCsv(
            listOf(
                HistoryEntry("12 × 7", "84"),
                HistoryEntry("say \"hi\", then 1+1", "2"),
            ),
        )
        val lines = csv.trim().lines()
        assertEquals("expression,result", lines[0])
        assertEquals("\"12 × 7\",\"84\"", lines[1])
        // The comma inside the field must not split it, and the quotes double.
        assertEquals("\"say \"\"hi\"\", then 1+1\",\"2\"", lines[2])
    }

    @Test
    fun `an empty history still produces a header`() {
        assertEquals("expression,result", Backup.historyCsv(emptyList()).trim())
    }

    @Test
    fun `suggested names carry a date and the right extension`() {
        val name = Backup.suggestedName("neocalc-backup", "json")
        assertTrue(name.startsWith("neocalc-backup-"))
        assertTrue(name.endsWith(".json"))
        assertTrue(name.contains(Regex("""\d{4}-\d{2}-\d{2}""")))
    }
}
