package com.omai.neocalc.convert

/**
 * Every unit is expressed as a linear map onto its category's base unit:
 *
 *     base = value * factor + offset
 *
 * Offsets exist only for temperature, but keeping one shape for all categories
 * means a single conversion routine rather than a special case per category.
 *
 * The one common category this cannot express is fuel economy: L/100km is the
 * *reciprocal* of km/L, not a scaling of it, so it would need its own model.
 * The linear members (km/L, mpg) are grouped under [UnitCategory.FuelEconomy].
 */
data class Measure(
    val label: String,
    val symbol: String,
    val factor: Double,
    val offset: Double = 0.0,
)

enum class UnitCategory(val label: String, val units: List<Measure>) {
    Length(
        "Length",
        listOf(
            Measure("Nanometre", "nm", 1e-9),
            Measure("Micrometre", "µm", 1e-6),
            Measure("Millimetre", "mm", 0.001),
            Measure("Centimetre", "cm", 0.01),
            Measure("Metre", "m", 1.0),
            Measure("Kilometre", "km", 1000.0),
            Measure("Inch", "in", 0.0254),
            Measure("Foot", "ft", 0.3048),
            Measure("Yard", "yd", 0.9144),
            Measure("Fathom", "ftm", 1.8288),
            Measure("Chain", "ch", 20.1168),
            Measure("Furlong", "fur", 201.168),
            Measure("Mile", "mi", 1609.344),
            Measure("Nautical mile", "nmi", 1852.0),
            Measure("League", "lea", 4828.032),
            Measure("Ångström", "Å", 1e-10),
            Measure("Astronomical unit", "AU", 1.495978707e11),
            Measure("Light-year", "ly", 9.4607304725808e15),
            Measure("Parsec", "pc", 3.0856775814913673e16),
        ),
    ),
    Mass(
        "Mass",
        listOf(
            Measure("Microgram", "µg", 1e-9),
            Measure("Milligram", "mg", 0.000001),
            Measure("Gram", "g", 0.001),
            Measure("Kilogram", "kg", 1.0),
            Measure("Tonne", "t", 1000.0),
            Measure("Carat", "ct", 0.0002),
            Measure("Grain", "gr", 0.00006479891),
            Measure("Ounce", "oz", 0.028349523125),
            Measure("Pound", "lb", 0.45359237),
            Measure("Stone", "st", 6.35029318),
            Measure("Slug", "slug", 14.593902937206),
            Measure("Short ton (US)", "ton", 907.18474),
            Measure("Long ton (UK)", "long ton", 1016.0469088),
        ),
    ),
    Temperature(
        "Temperature",
        listOf(
            // Base is kelvin, which is what makes the offsets meaningful.
            Measure("Celsius", "°C", 1.0, 273.15),
            Measure("Fahrenheit", "°F", 5.0 / 9.0, 255.3722222222222),
            Measure("Kelvin", "K", 1.0),
            Measure("Rankine", "°R", 5.0 / 9.0),
            Measure("Réaumur", "°Ré", 1.25, 273.15),
        ),
    ),
    Area(
        "Area",
        listOf(
            Measure("Square millimetre", "mm²", 0.000001),
            Measure("Square centimetre", "cm²", 0.0001),
            Measure("Square metre", "m²", 1.0),
            Measure("Are", "a", 100.0),
            Measure("Hectare", "ha", 10000.0),
            Measure("Square kilometre", "km²", 1000000.0),
            Measure("Square inch", "in²", 0.00064516),
            Measure("Square foot", "ft²", 0.09290304),
            Measure("Square yard", "yd²", 0.83612736),
            Measure("Acre", "ac", 4046.8564224),
            Measure("Square mile", "mi²", 2589988.110336),
        ),
    ),
    Volume(
        "Volume",
        listOf(
            Measure("Millilitre", "mL", 0.001),
            Measure("Cubic centimetre", "cm³", 0.001),
            Measure("Litre", "L", 1.0),
            Measure("Cubic metre", "m³", 1000.0),
            Measure("Cubic inch", "in³", 0.016387064),
            Measure("Cubic foot", "ft³", 28.316846592),
            Measure("Cubic yard", "yd³", 764.554857984),
            Measure("Teaspoon (US)", "tsp", 0.00492892159375),
            Measure("Tablespoon (US)", "tbsp", 0.01478676478125),
            Measure("Fluid ounce (US)", "fl oz", 0.0295735295625),
            Measure("Cup (US)", "cup", 0.2365882365),
            Measure("Pint (US)", "pt", 0.473176473),
            Measure("Quart (US)", "qt", 0.946352946),
            Measure("Gallon (US)", "gal", 3.785411784),
            Measure("Fluid ounce (UK)", "fl oz UK", 0.0284130625),
            Measure("Pint (UK)", "pt UK", 0.56826125),
            Measure("Quart (UK)", "qt UK", 1.1365225),
            Measure("Gallon (UK)", "gal UK", 4.54609),
            Measure("Barrel (oil)", "bbl", 158.987294928),
        ),
    ),
    Speed(
        "Speed",
        listOf(
            Measure("Metre / second", "m/s", 1.0),
            Measure("Kilometre / hour", "km/h", 0.2777777777777778),
            Measure("Mile / hour", "mph", 0.44704),
            Measure("Foot / second", "ft/s", 0.3048),
            Measure("Knot", "kn", 0.5144444444444445),
            Measure("Mach (sea level)", "M", 340.29),
        ),
    ),
    Time(
        "Time",
        listOf(
            Measure("Nanosecond", "ns", 1e-9),
            Measure("Microsecond", "µs", 1e-6),
            Measure("Millisecond", "ms", 0.001),
            Measure("Second", "s", 1.0),
            Measure("Minute", "min", 60.0),
            Measure("Hour", "h", 3600.0),
            Measure("Day", "d", 86400.0),
            Measure("Week", "wk", 604800.0),
            Measure("Month (30 d)", "mo", 2592000.0),
            Measure("Year (365 d)", "yr", 31536000.0),
            Measure("Decade", "dec", 315360000.0),
            Measure("Century", "c", 3153600000.0),
        ),
    ),
    DigitalStorage(
        "Digital storage",
        listOf(
            Measure("Bit", "bit", 0.125),
            Measure("Byte", "B", 1.0),
            Measure("Kilobyte", "kB", 1000.0),
            Measure("Megabyte", "MB", 1e6),
            Measure("Gigabyte", "GB", 1e9),
            Measure("Terabyte", "TB", 1e12),
            Measure("Petabyte", "PB", 1e15),
            Measure("Kibibyte", "KiB", 1024.0),
            Measure("Mebibyte", "MiB", 1048576.0),
            Measure("Gibibyte", "GiB", 1073741824.0),
            Measure("Tebibyte", "TiB", 1.099511627776e12),
        ),
    ),
    DataRate(
        "Data rate",
        listOf(
            Measure("Bit / second", "bit/s", 1.0),
            Measure("Kilobit / second", "kbit/s", 1000.0),
            Measure("Megabit / second", "Mbit/s", 1e6),
            Measure("Gigabit / second", "Gbit/s", 1e9),
            Measure("Byte / second", "B/s", 8.0),
            Measure("Kilobyte / second", "kB/s", 8000.0),
            Measure("Megabyte / second", "MB/s", 8e6),
        ),
    ),
    Pressure(
        "Pressure",
        listOf(
            Measure("Pascal", "Pa", 1.0),
            Measure("Hectopascal", "hPa", 100.0),
            Measure("Kilopascal", "kPa", 1000.0),
            Measure("Megapascal", "MPa", 1e6),
            Measure("Millibar", "mbar", 100.0),
            Measure("Bar", "bar", 100000.0),
            Measure("Atmosphere", "atm", 101325.0),
            Measure("Torr", "Torr", 133.32236842105263),
            Measure("Millimetre of mercury", "mmHg", 133.322387415),
            Measure("Inch of mercury", "inHg", 3386.388640341),
            Measure("Pound / square inch", "psi", 6894.757293168),
        ),
    ),
    Energy(
        "Energy",
        listOf(
            Measure("Joule", "J", 1.0),
            Measure("Kilojoule", "kJ", 1000.0),
            Measure("Calorie", "cal", 4.184),
            Measure("Kilocalorie", "kcal", 4184.0),
            Measure("Watt-hour", "Wh", 3600.0),
            Measure("Kilowatt-hour", "kWh", 3.6e6),
            Measure("Electronvolt", "eV", 1.602176634e-19),
            Measure("British thermal unit", "BTU", 1055.05585262),
            Measure("Foot-pound", "ft·lb", 1.3558179483314004),
            Measure("Therm", "thm", 105505585.262),
        ),
    ),
    Power(
        "Power",
        listOf(
            Measure("Milliwatt", "mW", 0.001),
            Measure("Watt", "W", 1.0),
            Measure("Kilowatt", "kW", 1000.0),
            Measure("Megawatt", "MW", 1e6),
            Measure("Horsepower (mech)", "hp", 745.6998715822702),
            Measure("Horsepower (metric)", "PS", 735.49875),
            Measure("BTU / hour", "BTU/h", 0.29307107017222),
        ),
    ),
    Force(
        "Force",
        listOf(
            Measure("Newton", "N", 1.0),
            Measure("Kilonewton", "kN", 1000.0),
            Measure("Dyne", "dyn", 1e-5),
            Measure("Kilogram-force", "kgf", 9.80665),
            Measure("Pound-force", "lbf", 4.4482216152605),
        ),
    ),
    Angle(
        "Angle",
        listOf(
            Measure("Degree", "°", 1.0),
            Measure("Radian", "rad", 57.29577951308232),
            Measure("Gradian", "grad", 0.9),
            Measure("Arcminute", "′", 1.0 / 60.0),
            Measure("Arcsecond", "″", 1.0 / 3600.0),
            Measure("Turn", "turn", 360.0),
        ),
    ),
    Frequency(
        "Frequency",
        listOf(
            Measure("Hertz", "Hz", 1.0),
            Measure("Kilohertz", "kHz", 1000.0),
            Measure("Megahertz", "MHz", 1e6),
            Measure("Gigahertz", "GHz", 1e9),
            Measure("Revolutions / minute", "rpm", 1.0 / 60.0),
        ),
    ),
    FuelEconomy(
        "Fuel economy",
        listOf(
            Measure("Kilometre / litre", "km/L", 1.0),
            Measure("Mile / gallon (US)", "mpg", 0.4251437074976577),
            Measure("Mile / gallon (UK)", "mpg UK", 0.3540061899600114),
            Measure("Mile / litre", "mi/L", 1.609344),
        ),
    ),
}

fun convert(value: Double, from: Measure, to: Measure): Double {
    val base = value * from.factor + from.offset
    return (base - to.offset) / to.factor
}
