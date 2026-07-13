package com.nanchat.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun NanChatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}