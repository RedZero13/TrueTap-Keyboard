package com.truetap.keyboard.ime

/** Key codes for keys that don't type a character. */
object Codes {
    const val SHIFT = -1
    const val SYMBOLS = -2
    const val ALPHA = -3
    const val SYMBOLS2 = -4
    const val DELETE = -5
    const val LANGUAGE = -6
    const val EMOJI = -7
    const val SPACE = 32
    const val ENTER = 10
}

enum class KeyType { LETTER, CHAR, FUNCTION, SPACE, ENTER }

data class KeySpec(
    val code: Int,
    val label: String,
    val type: KeyType,
    /** Width in key units; a full QWERTY row is 10 units. */
    val width: Float = 1f,
    /** Small label in the corner, typed on long-press. */
    val hint: String? = null,
    val alternates: List<String> = emptyList(),
    val icon: Icon? = null,
    /** Text to commit instead of the label (e.g. ".com"). */
    val text: String? = null,
)

class KeyboardLayout(
    /** Identifies the letter arrangement; touch learning is stored per id. */
    val id: String,
    val rows: List<List<KeySpec>>,
    /** Relative row heights (1 = a standard row). */
    val rowHeights: List<Float>,
    val isAlphabetic: Boolean,
    /** Units per row; shorter rows are centred and their edge keys absorb the slack for touch. */
    val unitsPerRow: Float = 10f,
)

/** Builds the per-language layouts plus the shared symbol and number pads. */
object Layouts {
    private val NUMBER_ALTS = mapOf(
        "1" to listOf("¹", "½", "⅓", "¼", "⅛"), "2" to listOf("²", "⅔"), "3" to listOf("³", "¾", "⅜"),
        "4" to listOf("⁴"), "5" to listOf("⅝"), "7" to listOf("⅞"), "0" to listOf("ⁿ", "∅", "°"),
    )

    /** Accented variants offered on long-press, most useful first per language. */
    private val ACCENTS: Map<String, Map<Char, String>> = mapOf(
        "en" to mapOf('a' to "àáâäæãåā", 'c' to "çćč", 'e' to "èéêëē", 'i' to "îïíīì", 'n' to "ñń",
            'o' to "ôöòóœøōõ", 's' to "ßśš", 'u' to "ûüùúū", 'y' to "ÿ"),
        "es" to mapOf('a' to "áàäâãåā", 'e' to "éèëêē", 'i' to "íìïîī", 'o' to "óòöôõøō", 'u' to "úüùûū",
            'c' to "ç", 'n' to "ñ"),
        "fr" to mapOf('a' to "àâæáäãåā", 'c' to "çćč", 'e' to "éèêëē", 'i' to "îïíīì", 'o' to "ôœöòóøō",
            'u' to "ùûüúū", 'y' to "ÿ"),
        "de" to mapOf('a' to "äàáâæãåā", 'e' to "éèêëė", 'i' to "îïíīì", 'o' to "öôòóœøō", 's' to "ßśš",
            'u' to "üûùúū", 'n' to "ñń"),
        "it" to mapOf('a' to "àáâäæãåā", 'e' to "èéêëē", 'i' to "ìíîïī", 'o' to "òóôöœøō", 'u' to "ùúûüū"),
        "pt" to mapOf('a' to "ãáàâäåæª", 'c' to "ç", 'e' to "éêèëē", 'i' to "íìîïī", 'o' to "õóôòöøº",
            'u' to "úüùûū"),
    )

    private val PERIOD_ALTS = listOf(",", "?", "!", "'", "\"", ":", ";", "-", "/", "@", "#", "&", "%", "(", ")")
    private val COMMA_ALTS = listOf(";", ":", "…")

    fun letters(lang: String, layoutId: String, numberRow: Boolean, multiLanguage: Boolean, enterIcon: Icon, emailOrUrl: Boolean): KeyboardLayout {
        val accents = ACCENTS[lang] ?: ACCENTS.getValue("en")
        val rowsText: List<String> = when (layoutId) {
            "azerty" -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")
            "qwertz" -> listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
            "qwerty_es" -> listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")
            else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        val units = if (layoutId == "qwertz") 11f else 10f
        val rows = ArrayList<List<KeySpec>>()
        val heights = ArrayList<Float>()
        if (numberRow) {
            rows.add("1234567890".map { KeySpec(it.code, it.toString(), KeyType.CHAR, units / 10f, alternates = NUMBER_ALTS[it.toString()] ?: emptyList()) })
            heights.add(NUMBER_ROW_HEIGHT)
        }
        rowsText.forEachIndexed { r, chars ->
            val row = ArrayList<KeySpec>()
            if (r == 2) row.add(KeySpec(Codes.SHIFT, "", KeyType.FUNCTION, 1.5f, icon = Icon.SHIFT))
            chars.forEachIndexed { i, c ->
                val digitHint = if (r == 0 && !numberRow && i < 10) "1234567890"[i].toString() else null
                val alts = ArrayList<String>()
                if (digitHint != null) alts.add(digitHint)
                accents[c]?.forEach { alts.add(it.toString()) }
                if (c == '\'') {
                    row.add(KeySpec(c.code, "'", KeyType.CHAR, 1f, alternates = listOf("\"", "’", "‘", "«", "»")))
                } else {
                    row.add(KeySpec(c.code, c.toString(), KeyType.LETTER, 1f, hint = digitHint, alternates = alts))
                }
            }
            if (r == 2) row.add(KeySpec(Codes.DELETE, "", KeyType.FUNCTION, 1.5f, icon = Icon.DELETE))
            rows.add(row)
            heights.add(1f)
        }
        // Make the third row exactly fill: shift and delete share what the letters leave.
        val third = rows[rows.size - 1]
        val letterUnits = third.count { it.type != KeyType.FUNCTION }.toFloat()
        val side = (units - letterUnits) / 2f
        rows[rows.size - 1] = third.map { if (it.type == KeyType.FUNCTION) it.copy(width = side) else it }

        rows.add(bottomRow(units, multiLanguage, enterIcon, emailOrUrl, alphaMode = true))
        heights.add(1f)
        return KeyboardLayout(layoutId, rows, heights, isAlphabetic = true, unitsPerRow = units)
    }

    private fun bottomRow(units: Float, multiLanguage: Boolean, enterIcon: Icon, emailOrUrl: Boolean, alphaMode: Boolean): List<KeySpec> {
        val k = units / 10f
        val row = ArrayList<KeySpec>()
        row.add(KeySpec(if (alphaMode) Codes.SYMBOLS else Codes.ALPHA, if (alphaMode) "?123" else "ABC", KeyType.FUNCTION, 1.5f * k))
        if (emailOrUrl) {
            row.add(KeySpec('@'.code, "@", KeyType.CHAR, 1f * k))
        } else {
            row.add(KeySpec(','.code, ",", KeyType.CHAR, 1f * k, alternates = COMMA_ALTS))
        }
        if (multiLanguage) {
            row.add(KeySpec(Codes.LANGUAGE, "", KeyType.FUNCTION, 1f * k, icon = Icon.GLOBE))
        } else {
            row.add(KeySpec(Codes.EMOJI, "", KeyType.FUNCTION, 1f * k, icon = Icon.EMOJI))
        }
        row.add(KeySpec(Codes.SPACE, "", KeyType.SPACE, 4f * k))
        if (emailOrUrl) {
            row.add(KeySpec('.'.code, ".", KeyType.CHAR, 1f * k, alternates = listOf(".com", ".net", ".org", "/", "-", "_", ":")))
        } else {
            row.add(KeySpec('.'.code, ".", KeyType.CHAR, 1f * k, alternates = PERIOD_ALTS))
        }
        row.add(KeySpec(Codes.ENTER, "", KeyType.ENTER, 1.5f * k, icon = enterIcon))
        return row
    }

    private fun charRow(chars: String, alts: Map<String, List<String>> = emptyMap()) =
        chars.map { KeySpec(it.code, it.toString(), KeyType.CHAR, 1f, alternates = alts[it.toString()] ?: emptyList()) }

    fun symbols(page: Int, multiLanguage: Boolean, enterIcon: Icon): KeyboardLayout {
        val rows = ArrayList<List<KeySpec>>()
        if (page == 1) {
            rows.add(charRow("1234567890", NUMBER_ALTS))
            rows.add(charRow("@#\$_&-+()/", mapOf("\$" to listOf("€", "£", "¥", "₹", "¢", "₩"), "-" to listOf("–", "—", "·"),
                "(" to listOf("<", "[", "{"), ")" to listOf(">", "]", "}"), "&" to listOf("§"), "+" to listOf("±"))))
            rows.add(listOf(KeySpec(Codes.SYMBOLS2, "=\\<", KeyType.FUNCTION, 1.5f)) +
                charRow("*\"':;!?", mapOf("*" to listOf("★", "†", "‡"), "\"" to listOf("“", "”", "„", "«", "»"),
                    "'" to listOf("‘", "’", "‚", "‹", "›"), "!" to listOf("¡"), "?" to listOf("¿"))) +
                KeySpec(Codes.DELETE, "", KeyType.FUNCTION, 1.5f, icon = Icon.DELETE))
        } else {
            rows.add(charRow("~`|•√π÷×¶∆"))
            rows.add(charRow("£¢€¥^°={}\\"))
            rows.add(listOf(KeySpec(Codes.SYMBOLS, "?123", KeyType.FUNCTION, 1.5f)) + charRow("%©®™✓[]") +
                KeySpec(Codes.DELETE, "", KeyType.FUNCTION, 1.5f, icon = Icon.DELETE))
        }
        rows.add(bottomRow(10f, multiLanguage, enterIcon, emailOrUrl = false, alphaMode = false))
        return KeyboardLayout("symbols$page", rows, listOf(1f, 1f, 1f, 1f), isAlphabetic = false)
    }

    fun numberPad(enterIcon: Icon, phone: Boolean): KeyboardLayout {
        fun k(c: String, alts: List<String> = emptyList()) = KeySpec(c[0].code, c, KeyType.CHAR, 2.5f, alternates = alts)
        val rows = listOf(
            listOf(k("1"), k("2"), k("3"), if (phone) k("+", listOf("*", "#", "(", ")")) else k("-", listOf("+", "/", "*"))),
            listOf(k("4"), k("5"), k("6"), KeySpec(Codes.SPACE, "", KeyType.SPACE, 2.5f)),
            listOf(k("7"), k("8"), k("9"), KeySpec(Codes.DELETE, "", KeyType.FUNCTION, 2.5f, icon = Icon.DELETE)),
            listOf(KeySpec(Codes.SYMBOLS, "?123", KeyType.FUNCTION, 2.5f), k("0"), k(if (phone) "#" else ".", listOf(",")),
                KeySpec(Codes.ENTER, "", KeyType.ENTER, 2.5f, icon = enterIcon)),
        )
        return KeyboardLayout("numpad", rows, listOf(1f, 1f, 1f, 1f), isAlphabetic = false)
    }

    const val NUMBER_ROW_HEIGHT = 0.9f
}
