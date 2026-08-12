package com.omai.neocalc.convert

import com.omai.neocalc.alerts.AlertDirection
import com.omai.neocalc.alerts.RateAlert
import com.omai.neocalc.alerts.RateAlerts
import com.omai.neocalc.calculator.DisplayFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CustomUnitsTest {

    // Volume's base unit is the litre, so a 24-litre crate has a factor of 24.
    private val crate = CustomUnit("Crate", "cr", UnitCategory.Volume, 24.0)

    @Test
    fun `a custom unit survives a round trip`() {
        val decoded = CustomUnits.decode(CustomUnits.encode(listOf(crate)))
        assertEquals(listOf(crate), decoded)
    }

    @Test
    fun `unreadable storage decodes to nothing rather than throwing`() {
        assertEquals(emptyList<CustomUnit>(), CustomUnits.decode(null))
        assertEquals(emptyList<CustomUnit>(), CustomUnits.decode("not json"))
        assertEquals(emptyList<CustomUnit>(), CustomUnits.decode("[{}]"))
    }

    @Test
    fun `a unit with an impossible factor is dropped`() {
        val bad = """[{"label":"X","symbol":"x","category":"Length","factor":0}]"""
        assertEquals(emptyList<CustomUnit>(), CustomUnits.decode(bad))
    }

    @Test
    fun `a custom unit converts like a built-in one`() {
        // A crate defined as 24 litres should convert to litres as 24.
        val measure = crate.toMeasure()
        val litre = UnitCategory.Volume.units.first { it.symbol == "L" }
        assertEquals(24.0, convert(1.0, measure, litre), 1e-6)
    }
}

class RateAlertsTest {

    private val alert = RateAlert("id", "USD", "NGN", 1600.0, AlertDirection.Above)

    @Test
    fun `an above alert fires at or past its target`() {
        assertTrue(alert.triggeredBy(1600.0))
        assertTrue(alert.triggeredBy(1700.0))
        assertFalse(alert.triggeredBy(1599.99))
    }

    @Test
    fun `a below alert is the mirror image`() {
        val below = alert.copy(direction = AlertDirection.Below)
        assertTrue(below.triggeredBy(1600.0))
        assertTrue(below.triggeredBy(1500.0))
        assertFalse(below.triggeredBy(1600.01))
    }

    @Test
    fun `alerts survive a round trip through storage`() {
        val list = listOf(alert, alert.copy(id = "two", armed = false, lastFired = 42L))
        assertEquals(list, RateAlerts.decode(RateAlerts.encode(list)))
    }

    @Test
    fun `a corrupt alert list decodes to empty`() {
        assertEquals(emptyList<RateAlert>(), RateAlerts.decode("{"))
        assertEquals(emptyList<RateAlert>(), RateAlerts.decode(null))
    }
}

class DisplayFormatTest {

    @Test
    fun `thousands are grouped`() {
        assertEquals("1,024", DisplayFormat.forDisplay("1024", Locale.UK))
        assertEquals("1,234,567", DisplayFormat.forDisplay("1234567", Locale.UK))
    }

    @Test
    fun `an entry still being typed is left alone`() {
        assertEquals("0.", DisplayFormat.forDisplay("0.", Locale.UK))
        assertEquals("-", DisplayFormat.forDisplay("-", Locale.UK))
        assertEquals("12.", DisplayFormat.forDisplay("12.", Locale.UK))
    }

    @Test
    fun `very large and very small numbers switch to exponent form`() {
        assertTrue(DisplayFormat.forDisplay("1000000000000000000", Locale.UK).contains("e+"))
        assertTrue(DisplayFormat.forDisplay("0.00000001", Locale.UK).contains("e-"))
    }

    @Test
    fun `ordinary numbers never use exponent form`() {
        listOf("0", "1", "-42", "3.14159", "999999999999").forEach {
            assertFalse(it, DisplayFormat.forDisplay(it, Locale.UK).contains("e"))
        }
    }

    @Test
    fun `an error message passes through untouched`() {
        assertEquals("Can't divide by zero", DisplayFormat.forDisplay("Can't divide by zero"))
    }

    @Test
    fun `the font shrinks as the number grows, and never past a floor`() {
        assertEquals(1f, DisplayFormat.fontScaleFor("1234"), 1e-6f)
        assertTrue(DisplayFormat.fontScaleFor("1234567890123") < 1f)
        assertTrue(DisplayFormat.fontScaleFor("1".repeat(40)) >= 0.5f)
    }
}
