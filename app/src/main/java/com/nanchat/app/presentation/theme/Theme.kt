package com.nanchat.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** NanChat's custom brand color scheme - see [NanChatColors] for the palette and its derivation. */
private val NanChatColorScheme = ColorScheme(
    primary = NanChatColors.Primary90,
    primaryDim = NanChatColors.Primary80,
    primaryContainer = NanChatColors.Primary30,
    onPrimary = NanChatColors.Primary10,
    onPrimaryContainer = NanChatColors.Primary95,
    secondary = NanChatColors.Secondary90,
    secondaryDim = NanChatColors.Secondary80,
    secondaryContainer = NanChatColors.Secondary30,
    onSecondary = NanChatColors.Secondary10,
    onSecondaryContainer = NanChatColors.Secondary95,
    tertiary = NanChatColors.Tertiary90,
    tertiaryDim = NanChatColors.Tertiary80,
    tertiaryContainer = NanChatColors.Tertiary30,
    onTertiary = NanChatColors.Tertiary10,
    onTertiaryContainer = NanChatColors.Tertiary95,
    surfaceContainerLow = NanChatColors.Neutral15,
    surfaceContainer = NanChatColors.Neutral20,
    surfaceContainerHigh = NanChatColors.Neutral30,
    onSurface = NanChatColors.Neutral95,
    onSurfaceVariant = NanChatColors.NeutralVariant80,
    outline = NanChatColors.NeutralVariant60,
    outlineVariant = NanChatColors.NeutralVariant40,
    background = NanChatColors.NeutralBlack,
    onBackground = NanChatColors.NeutralWhite,
)

@Composable
fun NanChatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NanChatColorScheme,
        content = content
    )
}