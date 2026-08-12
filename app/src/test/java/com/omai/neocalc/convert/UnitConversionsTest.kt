package com.omai.neocalc.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConversionsTest {

    private fun unit(category: UnitCategory, symbol: String): Measure =
        category.units.first { it.symbol == symbol }

    private fun convert(
        value: Double,
        category: UnitCategory,
        from: String,
        to: String,
    ): Double = convert(value, unit(category, from), unit(category, to))

    @Test
    fun `length conversions match known values`() {
        assertEquals(1000.0, convert(1.0, UnitCategory.Length, "km", "m"), 1e-9)
        assertEquals(2.54, convert(1.0, UnitCategory.Length, "in", "cm"), 1e-9)
        assertEquals(5280.0, convert(1.0, UnitCategory.Length, "mi", "ft"), 1e-6)
        assertEquals(1.0, convert(1.0, UnitCategory.Length, "ly", "ly"), 1e-9)
    }

    @Test
    fun `mass conversions match known values`() {
        assertEquals(16.0, convert(1.0, UnitCategory.Mass, "lb", "oz"), 1e-9)
        assertEquals(14.0, convert(1.0, UnitCategory.Mass, "st", "lb"), 1e-9)
        assertEquals(1000.0, convert(1.0, UnitCategory.Mass, "kg", "g"), 1e-9)
    }

    @Test
    fun `temperature handles offsets in both directions`() {
        assertEquals(32.0, convert(0.0, UnitCategory.Temperature, "°C", "°F"), 1e-9)
        assertEquals(212.0, convert(100.0, UnitCategory.Temperature, "°C", "°F"), 1e-9)
        assertEquals(-40.0, convert(-40.0, UnitCategory.Temperature, "°C", "°F"), 1e-9)
        assertEquals(273.15, convert(0.0, UnitCategory.Temperature, "°C", "K"), 1e-9)
        assertEquals(491.67, convert(32.0, UnitCategory.Temperature, "°F", "°R"), 1e-9)
        assertEquals(80.0, convert(100.0, UnitCategory.Temperature, "°C", "°Ré"), 1e-9)
    }

    @Test
    fun `digital storage separates decimal and binary prefixes`() {
        assertEquals(8.0, convert(1.0, UnitCategory.DigitalStorage, "B", "bit"), 1e-9)
        assertEquals(1000.0, convert(1.0, UnitCategory.DigitalStorage, "MB", "kB"), 1e-9)
        assertEquals(1024.0, convert(1.0, UnitCategory.DigitalStorage, "MiB", "KiB"), 1e-9)
        // The classic discrepancy: a "1 TB" disk is ~931 GiB.
        assertEquals(931.32, convert(1.0, UnitCategory.DigitalStorage, "TB", "GiB"), 0.01)
    }

    @Test
    fun `time speed and angle conversions`() {
        assertEquals(1440.0, convert(1.0, UnitCategory.Time, "d", "min"), 1e-9)
        assertEquals(3.6, convert(1.0, UnitCategory.Speed, "m/s", "km/h"), 1e-9)
        assertEquals(360.0, convert(1.0, UnitCategory.Angle, "turn", "°"), 1e-9)
        assertEquals(180.0, convert(Math.PI, UnitCategory.Angle, "rad", "°"), 1e-9)
    }

    @Test
    fun `pressure energy and power conversions`() {
        assertEquals(101325.0, convert(1.0, UnitCategory.Pressure, "atm", "Pa"), 1e-6)
        assertEquals(14.6959, convert(1.0, UnitCategory.Pressure, "atm", "psi"), 1e-4)
        assertEquals(3600.0, convert(1.0, UnitCategory.Energy, "Wh", "J"), 1e-9)
        assertEquals(1000.0, convert(1.0, UnitCategory.Energy, "kcal", "cal"), 1e-9)
        assertEquals(745.6998, convert(1.0, UnitCategory.Power, "hp", "W"), 1e-3)
    }

    @Test
    fun `fuel economy stays within its linear members`() {
        assertEquals(1.609344, convert(1.0, UnitCategory.FuelEconomy, "mi/L", "km/L"), 1e-9)
        assertEquals(2.352, convert(1.0, UnitCategory.FuelEconomy, "km/L", "mpg"), 1e-3)
        // L/100km is the reciprocal of km/L, so it is deliberately absent.
        assertTrue(UnitCategory.FuelEconomy.units.none { it.symbol.contains("100") })
    }

    @Test
    fun `every unit round trips through its base`() {
        UnitCategory.entries.forEach { category ->
            category.units.forEach { from ->
                category.units.forEach { to ->
                    val there = convert(7.5, from, to)
                    val back = convert(there, to, from)
                    assertEquals(
                        "${category.label}: ${from.symbol} -> ${to.symbol}",
                        7.5,
                        back,
                        1e-6,
                    )
                }
            }
        }
    }

    @Test
    fun `every category offers at least two distinct units`() {
        UnitCategory.entries.forEach { category ->
            assertTrue(category.label, category.units.size >= 2)
            val symbols = category.units.map { it.symbol }
            assertEquals(category.label, symbols.size, symbols.toSet().size)
        }
    }
}
