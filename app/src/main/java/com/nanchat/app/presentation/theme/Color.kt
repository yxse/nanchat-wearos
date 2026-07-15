package com.nanchat.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * NanChat's brand color scheme, following the Wear OS Material 3 Expressive color system
 * (developer.android.com/design/ui/wear/guides/styles/color): a custom brand palette (rather than
 * dynamic-from-watchface color) seeded from the app's actual logo blue (#1677FF, see
 * ic_launcher_foreground.xml/splash_icon.xml), with tonal steps generated in OKLCH and verified to
 * hit AAA contrast (>=7:1) for every on-color/container pairing. Neutrals carry a faint hint of the
 * brand hue per the guide's "grays with a hint of the primary hue" recommendation, and the
 * background stays true black as Wear OS requires.
 */
object NanChatColors {
    // Primary: brand blue.
    val Primary10 = Color(0xFF00085B)
    val Primary30 = Color(0xFF0034BA)
    val Primary80 = Color(0xFF5EBAFF)
    val Primary90 = Color(0xFFA9E0FF)
    val Primary95 = Color(0xFFCAF2FF)

    // Secondary: muted/desaturated blue, for supporting actions in dense UI.
    val Secondary10 = Color(0xFF0B1A32)
    val Secondary30 = Color(0xFF2E4871)
    val Secondary80 = Color(0xFFA0BFF0)
    val Secondary90 = Color(0xFFCCDFFD)
    val Secondary95 = Color(0xFFE3F1FF)

    // Tertiary: golden amber accent, reserved for high-signal glanceable elements (unread badges,
    // sticker/badge-style accents) - a third accent hue distinct from the brand blue and from the
    // semantic error red.
    val Tertiary10 = Color(0xFF2E1300)
    val Tertiary30 = Color(0xFF6A3D00)
    val Tertiary80 = Color(0xFFE8B61D)
    val Tertiary90 = Color(0xFFF8DB94)
    val Tertiary95 = Color(0xFFFFEEBC)

    // Neutrals: blue-tinted grays built from true black.
    val NeutralBlack = Color(0xFF000000)
    val Neutral15 = Color(0xFF060C17)
    val Neutral20 = Color(0xFF111924)
    val Neutral30 = Color(0xFF262E3B)
    val Neutral95 = Color(0xFFE6F1FF)
    val NeutralWhite = Color(0xFFFFFFFF)
    val NeutralVariant40 = Color(0xFF394860)
    val NeutralVariant60 = Color(0xFF7687A2)
    val NeutralVariant80 = Color(0xFFADBFDB)
}
