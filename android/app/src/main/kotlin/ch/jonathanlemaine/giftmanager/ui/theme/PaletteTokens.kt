package ch.jonathanlemaine.giftmanager.ui.theme

/**
 * Valeurs ARGB des tokens de couleur, clair et sombre. Source de vérité :
 * `ios/GiftManager/Resources/Assets.xcassets/Theme/<token>.colorset` (vérifié par `PaletteParityTest`).
 */
data class ThemeTokens(
    val background: Long,
    val surface: Long,
    val separator: Long,
    val primary: Long,
    val onPrimary: Long,
    val secondary: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    val heart: Long,
    val accentAmber: Long,
    val availableBg: Long,
    val availableFg: Long,
    val takenBg: Long,
    val takenFg: Long,
    val mineBg: Long,
    val mineFg: Long,
    val ownedBg: Long,
    val ownedFg: Long,
    val potBg: Long,
    val potFg: Long,
    val pastelPink: Long,
    val pastelMint: Long,
    val pastelBlue: Long,
    val pastelPeach: Long,
    val pastelLavender: Long,
) {
    fun pastel(pastel: Pastel): Long = when (pastel) {
        Pastel.PINK -> pastelPink
        Pastel.MINT -> pastelMint
        Pastel.BLUE -> pastelBlue
        Pastel.PEACH -> pastelPeach
        Pastel.LAVENDER -> pastelLavender
    }

    /** Valeur d'un token par son nom iOS (`textPrimary`, `pastelMint`…) ; `null` si inconnu. */
    fun byName(name: String): Long? = when (name) {
        "background" -> background
        "surface" -> surface
        "separator" -> separator
        "primary" -> primary
        "onPrimary" -> onPrimary
        "secondary" -> secondary
        "textPrimary" -> textPrimary
        "textSecondary" -> textSecondary
        "heart" -> heart
        "accentAmber" -> accentAmber
        "availableBg" -> availableBg
        "availableFg" -> availableFg
        "takenBg" -> takenBg
        "takenFg" -> takenFg
        "mineBg" -> mineBg
        "mineFg" -> mineFg
        "ownedBg" -> ownedBg
        "ownedFg" -> ownedFg
        "potBg" -> potBg
        "potFg" -> potFg
        "pastelPink" -> pastelPink
        "pastelMint" -> pastelMint
        "pastelBlue" -> pastelBlue
        "pastelPeach" -> pastelPeach
        "pastelLavender" -> pastelLavender
        else -> null
    }

    companion object {
        /** Noms des tokens, identiques aux dossiers `.colorset` iOS. */
        val names: List<String> = listOf(
            "background", "surface", "separator", "primary", "onPrimary", "secondary",
            "textPrimary", "textSecondary", "heart", "accentAmber",
            "availableBg", "availableFg", "takenBg", "takenFg", "mineBg", "mineFg",
            "ownedBg", "ownedFg", "potBg", "potFg",
            "pastelPink", "pastelMint", "pastelBlue", "pastelPeach", "pastelLavender",
        )

        val Light = ThemeTokens(
            background = 0xFFFBF4EC,
            surface = 0xFFFFFFFF,
            separator = 0xFFEAE2D6,
            primary = 0xFF1E2A47,
            onPrimary = 0xFFFFFFFF,
            secondary = 0xFF2A7464,
            textPrimary = 0xFF1B1F2A,
            textSecondary = 0xFF626673,
            heart = 0xFFE5484D,
            accentAmber = 0xFFB85F0E,
            availableBg = 0xFFDDF3E6,
            availableFg = 0xFF1E6B43,
            takenBg = 0xFFFDE3E3,
            takenFg = 0xFFB3302F,
            mineBg = 0xFFDCE8FB,
            mineFg = 0xFF234E9A,
            ownedBg = 0xFFECE7FA,
            ownedFg = 0xFF5A47A6,
            potBg = 0xFFFCEBD0,
            potFg = 0xFF8B4A0C,
            pastelPink = 0xFFF9D9DC,
            pastelMint = 0xFFD5F0E4,
            pastelBlue = 0xFFDCE8FB,
            pastelPeach = 0xFFFCE3D2,
            pastelLavender = 0xFFE6E1FA,
        )

        val Dark = ThemeTokens(
            background = 0xFF141A2C,
            surface = 0xFF232B43,
            separator = 0xFF343D59,
            primary = 0xFFA9BCEE,
            onPrimary = 0xFF141A2C,
            secondary = 0xFF6CC7AE,
            textPrimary = 0xFFF4F1EC,
            textSecondary = 0xFFA8AEBF,
            heart = 0xFFFF6B70,
            accentAmber = 0xFFF2A65A,
            availableBg = 0xFF1D3D2E,
            availableFg = 0xFF8EDBB0,
            takenBg = 0xFF45222A,
            takenFg = 0xFFFFA3A3,
            mineBg = 0xFF22365A,
            mineFg = 0xFFA9C6FF,
            ownedBg = 0xFF322B55,
            ownedFg = 0xFFCBBEFF,
            potBg = 0xFF47331A,
            potFg = 0xFFF7C98B,
            pastelPink = 0xFF4A2C36,
            pastelMint = 0xFF1F4237,
            pastelBlue = 0xFF233A5E,
            pastelPeach = 0xFF4A3326,
            pastelLavender = 0xFF352F5C,
        )
    }
}
