package com.omai.neocalc.convert

/**
 * Display metadata for an ISO 4217 code: the human name people actually search
 * by ("pound", "yen") and the region whose flag stands for it.
 */
data class CurrencyInfo(
    val code: String,
    val name: String,
    /** ISO 3166-1 alpha-2, or null for supranational codes that have no flag. */
    val region: String?,
    /** Used instead of a flag by things that are not a country's money. */
    val overrideFlag: String? = null,
) {
    /**
     * Regional-indicator pairs rather than bundled images: the platform already
     * ships every flag, so this costs no drawables and no download.
     */
    val flag: String
        get() = overrideFlag ?: region?.let { code ->
            buildString {
                code.forEach { appendCodePoint(FLAG_BASE + (it.uppercaseChar() - 'A')) }
            }
        } ?: NO_FLAG

    /** Matches on code, name, or region so "gb", "sterling" and "pound" all land. */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return code.contains(q, ignoreCase = true) ||
            name.contains(q, ignoreCase = true) ||
            region?.equals(q, ignoreCase = true) == true
    }

    private companion object {
        const val FLAG_BASE = 0x1F1E6
        const val NO_FLAG = "🏳️" // 🏳️ - supranational, no country
    }
}

/**
 * Names and flags for the ISO 4217 set the rate providers quote. Codes the API
 * returns that are missing here still work - [currencyInfo] falls back to the
 * bare code - so a new currency never breaks the picker.
 */
object Currencies {

    private val TABLE: Map<String, CurrencyInfo> = listOf(
        CurrencyInfo("AED", "UAE Dirham", "AE"),
        CurrencyInfo("AFN", "Afghan Afghani", "AF"),
        CurrencyInfo("ALL", "Albanian Lek", "AL"),
        CurrencyInfo("AMD", "Armenian Dram", "AM"),
        CurrencyInfo("ANG", "Netherlands Antillean Guilder", "CW"),
        CurrencyInfo("AOA", "Angolan Kwanza", "AO"),
        CurrencyInfo("ARS", "Argentine Peso", "AR"),
        CurrencyInfo("AUD", "Australian Dollar", "AU"),
        CurrencyInfo("AWG", "Aruban Florin", "AW"),
        CurrencyInfo("AZN", "Azerbaijani Manat", "AZ"),
        CurrencyInfo("BAM", "Bosnian Convertible Mark", "BA"),
        CurrencyInfo("BBD", "Barbadian Dollar", "BB"),
        CurrencyInfo("BDT", "Bangladeshi Taka", "BD"),
        CurrencyInfo("BGN", "Bulgarian Lev", "BG"),
        CurrencyInfo("BHD", "Bahraini Dinar", "BH"),
        CurrencyInfo("BIF", "Burundian Franc", "BI"),
        CurrencyInfo("BMD", "Bermudan Dollar", "BM"),
        CurrencyInfo("BND", "Brunei Dollar", "BN"),
        CurrencyInfo("BOB", "Bolivian Boliviano", "BO"),
        CurrencyInfo("BRL", "Brazilian Real", "BR"),
        CurrencyInfo("BSD", "Bahamian Dollar", "BS"),
        CurrencyInfo("BTN", "Bhutanese Ngultrum", "BT"),
        CurrencyInfo("BWP", "Botswanan Pula", "BW"),
        CurrencyInfo("BYN", "Belarusian Ruble", "BY"),
        CurrencyInfo("BZD", "Belize Dollar", "BZ"),
        CurrencyInfo("CAD", "Canadian Dollar", "CA"),
        CurrencyInfo("CDF", "Congolese Franc", "CD"),
        CurrencyInfo("CHF", "Swiss Franc", "CH"),
        CurrencyInfo("CLP", "Chilean Peso", "CL"),
        CurrencyInfo("CNY", "Chinese Yuan", "CN"),
        CurrencyInfo("COP", "Colombian Peso", "CO"),
        CurrencyInfo("CRC", "Costa Rican Colón", "CR"),
        CurrencyInfo("CUP", "Cuban Peso", "CU"),
        CurrencyInfo("CVE", "Cape Verdean Escudo", "CV"),
        CurrencyInfo("CZK", "Czech Koruna", "CZ"),
        CurrencyInfo("DJF", "Djiboutian Franc", "DJ"),
        CurrencyInfo("DKK", "Danish Krone", "DK"),
        CurrencyInfo("DOP", "Dominican Peso", "DO"),
        CurrencyInfo("DZD", "Algerian Dinar", "DZ"),
        CurrencyInfo("EGP", "Egyptian Pound", "EG"),
        CurrencyInfo("ERN", "Eritrean Nakfa", "ER"),
        CurrencyInfo("ETB", "Ethiopian Birr", "ET"),
        CurrencyInfo("EUR", "Euro", "EU"),
        CurrencyInfo("FJD", "Fijian Dollar", "FJ"),
        CurrencyInfo("FKP", "Falkland Islands Pound", "FK"),
        CurrencyInfo("FOK", "Faroese Króna", "FO"),
        CurrencyInfo("GBP", "British Pound Sterling", "GB"),
        CurrencyInfo("GEL", "Georgian Lari", "GE"),
        CurrencyInfo("GGP", "Guernsey Pound", "GG"),
        CurrencyInfo("GHS", "Ghanaian Cedi", "GH"),
        CurrencyInfo("GIP", "Gibraltar Pound", "GI"),
        CurrencyInfo("GMD", "Gambian Dalasi", "GM"),
        CurrencyInfo("GNF", "Guinean Franc", "GN"),
        CurrencyInfo("GTQ", "Guatemalan Quetzal", "GT"),
        CurrencyInfo("GYD", "Guyanaese Dollar", "GY"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK"),
        CurrencyInfo("HNL", "Honduran Lempira", "HN"),
        CurrencyInfo("HRK", "Croatian Kuna", "HR"),
        CurrencyInfo("HTG", "Haitian Gourde", "HT"),
        CurrencyInfo("HUF", "Hungarian Forint", "HU"),
        CurrencyInfo("IDR", "Indonesian Rupiah", "ID"),
        CurrencyInfo("ILS", "Israeli New Shekel", "IL"),
        CurrencyInfo("IMP", "Manx Pound", "IM"),
        CurrencyInfo("INR", "Indian Rupee", "IN"),
        CurrencyInfo("IQD", "Iraqi Dinar", "IQ"),
        CurrencyInfo("IRR", "Iranian Rial", "IR"),
        CurrencyInfo("ISK", "Icelandic Króna", "IS"),
        CurrencyInfo("JEP", "Jersey Pound", "JE"),
        CurrencyInfo("JMD", "Jamaican Dollar", "JM"),
        CurrencyInfo("JOD", "Jordanian Dinar", "JO"),
        CurrencyInfo("JPY", "Japanese Yen", "JP"),
        CurrencyInfo("KES", "Kenyan Shilling", "KE"),
        CurrencyInfo("KGS", "Kyrgystani Som", "KG"),
        CurrencyInfo("KHR", "Cambodian Riel", "KH"),
        CurrencyInfo("KID", "Kiribati Dollar", "KI"),
        CurrencyInfo("KMF", "Comorian Franc", "KM"),
        CurrencyInfo("KRW", "South Korean Won", "KR"),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "KW"),
        CurrencyInfo("KYD", "Cayman Islands Dollar", "KY"),
        CurrencyInfo("KZT", "Kazakhstani Tenge", "KZ"),
        CurrencyInfo("LAK", "Laotian Kip", "LA"),
        CurrencyInfo("LBP", "Lebanese Pound", "LB"),
        CurrencyInfo("LKR", "Sri Lankan Rupee", "LK"),
        CurrencyInfo("LRD", "Liberian Dollar", "LR"),
        CurrencyInfo("LSL", "Lesotho Loti", "LS"),
        CurrencyInfo("LYD", "Libyan Dinar", "LY"),
        CurrencyInfo("MAD", "Moroccan Dirham", "MA"),
        CurrencyInfo("MDL", "Moldovan Leu", "MD"),
        CurrencyInfo("MGA", "Malagasy Ariary", "MG"),
        CurrencyInfo("MKD", "Macedonian Denar", "MK"),
        CurrencyInfo("MMK", "Myanmar Kyat", "MM"),
        CurrencyInfo("MNT", "Mongolian Tugrik", "MN"),
        CurrencyInfo("MOP", "Macanese Pataca", "MO"),
        CurrencyInfo("MRU", "Mauritanian Ouguiya", "MR"),
        CurrencyInfo("MUR", "Mauritian Rupee", "MU"),
        CurrencyInfo("MVR", "Maldivian Rufiyaa", "MV"),
        CurrencyInfo("MWK", "Malawian Kwacha", "MW"),
        CurrencyInfo("MXN", "Mexican Peso", "MX"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "MY"),
        CurrencyInfo("MZN", "Mozambican Metical", "MZ"),
        CurrencyInfo("NAD", "Namibian Dollar", "NA"),
        CurrencyInfo("NGN", "Nigerian Naira", "NG"),
        CurrencyInfo("NIO", "Nicaraguan Córdoba", "NI"),
        CurrencyInfo("NOK", "Norwegian Krone", "NO"),
        CurrencyInfo("NPR", "Nepalese Rupee", "NP"),
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ"),
        CurrencyInfo("OMR", "Omani Rial", "OM"),
        CurrencyInfo("PAB", "Panamanian Balboa", "PA"),
        CurrencyInfo("PEN", "Peruvian Sol", "PE"),
        CurrencyInfo("PGK", "Papua New Guinean Kina", "PG"),
        CurrencyInfo("PHP", "Philippine Peso", "PH"),
        CurrencyInfo("PKR", "Pakistani Rupee", "PK"),
        CurrencyInfo("PLN", "Polish Złoty", "PL"),
        CurrencyInfo("PYG", "Paraguayan Guarani", "PY"),
        CurrencyInfo("QAR", "Qatari Riyal", "QA"),
        CurrencyInfo("RON", "Romanian Leu", "RO"),
        CurrencyInfo("RSD", "Serbian Dinar", "RS"),
        CurrencyInfo("RUB", "Russian Ruble", "RU"),
        CurrencyInfo("RWF", "Rwandan Franc", "RW"),
        CurrencyInfo("SAR", "Saudi Riyal", "SA"),
        CurrencyInfo("SBD", "Solomon Islands Dollar", "SB"),
        CurrencyInfo("SCR", "Seychellois Rupee", "SC"),
        CurrencyInfo("SDG", "Sudanese Pound", "SD"),
        CurrencyInfo("SEK", "Swedish Krona", "SE"),
        CurrencyInfo("SGD", "Singapore Dollar", "SG"),
        CurrencyInfo("SHP", "Saint Helena Pound", "SH"),
        CurrencyInfo("SLE", "Sierra Leonean Leone", "SL"),
        CurrencyInfo("SOS", "Somali Shilling", "SO"),
        CurrencyInfo("SRD", "Surinamese Dollar", "SR"),
        CurrencyInfo("SSP", "South Sudanese Pound", "SS"),
        CurrencyInfo("STN", "São Tomé & Príncipe Dobra", "ST"),
        CurrencyInfo("SYP", "Syrian Pound", "SY"),
        CurrencyInfo("SZL", "Swazi Lilangeni", "SZ"),
        CurrencyInfo("THB", "Thai Baht", "TH"),
        CurrencyInfo("TJS", "Tajikistani Somoni", "TJ"),
        CurrencyInfo("TMT", "Turkmenistani Manat", "TM"),
        CurrencyInfo("TND", "Tunisian Dinar", "TN"),
        CurrencyInfo("TOP", "Tongan Paʻanga", "TO"),
        CurrencyInfo("TRY", "Turkish Lira", "TR"),
        CurrencyInfo("TTD", "Trinidad & Tobago Dollar", "TT"),
        CurrencyInfo("TVD", "Tuvaluan Dollar", "TV"),
        CurrencyInfo("TWD", "New Taiwan Dollar", "TW"),
        CurrencyInfo("TZS", "Tanzanian Shilling", "TZ"),
        CurrencyInfo("UAH", "Ukrainian Hryvnia", "UA"),
        CurrencyInfo("UGX", "Ugandan Shilling", "UG"),
        CurrencyInfo("USD", "US Dollar", "US"),
        CurrencyInfo("UYU", "Uruguayan Peso", "UY"),
        CurrencyInfo("UZS", "Uzbekistani Som", "UZ"),
        CurrencyInfo("VES", "Venezuelan Bolívar", "VE"),
        CurrencyInfo("VND", "Vietnamese Dong", "VN"),
        CurrencyInfo("VUV", "Vanuatu Vatu", "VU"),
        CurrencyInfo("WST", "Samoan Tala", "WS"),
        CurrencyInfo("XAF", "Central African CFA Franc", null),
        CurrencyInfo("XCD", "East Caribbean Dollar", null),
        CurrencyInfo("XCG", "Caribbean Guilder", null),
        CurrencyInfo("XDR", "IMF Special Drawing Rights", null),
        CurrencyInfo("XOF", "West African CFA Franc", null),
        CurrencyInfo("XPF", "CFP Franc", null),
        CurrencyInfo("YER", "Yemeni Rial", "YE"),
        CurrencyInfo("ZAR", "South African Rand", "ZA"),
        CurrencyInfo("ZMW", "Zambian Kwacha", "ZM"),
        CurrencyInfo("ZWL", "Zimbabwean Dollar", "ZW"),
    ).associateBy { it.code }

    /**
     * Never null: an unknown code still renders as itself with a neutral flag.
     * Crypto and metals have no country, so they carry their own glyph instead
     * of a flag - see [CurrencyInfo.overrideFlag].
     */
    fun info(code: String): CurrencyInfo = TABLE[code] ?: when {
        DigitalAssets.isAsset(code) -> CurrencyInfo(
            code = code,
            name = DigitalAssets.nameOf(code),
            region = null,
            overrideFlag = DigitalAssets.glyphOf(code),
        )

        else -> CurrencyInfo(code, code, null)
    }
}
