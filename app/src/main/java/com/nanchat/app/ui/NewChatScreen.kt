package com.nanchat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

@Composable
fun NewChatScreen(
    loading: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onStartChat: (String) -> Unit,
) {
    var username by remember { mutableStateOf(TextFieldValue("")) }
    var composing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // The field's very first focus state after entering composition is "unfocused" (focus is only
    // requested a moment later), so only treat it as a genuine dismissal once it has actually held
    // focus at least once.
    var hasFocusedOnce by remember(composing) { mutableStateOf(false) }

    fun submit() {
        if (username.text.isNotBlank() && !loading) {
            onDismissError()
            onStartChat(username.text.trim())
        }
    }

    // Consume back presses while composing to just collapse back to the icon (when nothing was
    // typed), instead of letting them fall through to the outer navigation back handler.
    BackHandler(enabled = composing) {
        if (username.text.isBlank()) composing = false
    }

    AppScaffold {
        ScreenScaffold { contentPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (loading) {
                        ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, indicatorSize = 40.dp)
                    } else if (!composing) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                .clickable { composing = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Keyboard, contentDescription = null)
                        }
                    }
                    // Stays mounted at all times (just zero-sized when idle): creating the field fresh
                    // right as focus is requested can bind a dummy InputConnection on this device.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (composing && !loading) Modifier else Modifier.size(0.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = username,
                            onValueChange = { username = it },
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodySmall.fontSize),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        hasFocusedOnce = true
                                    } else if (composing && hasFocusedOnce && username.text.isBlank()) {
                                        composing = false
                                    }
                                }
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = ::submit) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                        }
                    }
                }
                if (!composing && !loading) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        // Same field as the phone/web app's `username` - just labeled "NanChat ID" in
                        // their UI everywhere (AccountInfo.tsx, MyAccount.tsx, SetUsername.tsx).
                        text = "NanChat ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (error != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }

    LaunchedEffect(composing) {
        if (composing) {
            repeat(5) {
                focusRequester.requestFocus()
                kotlinx.coroutines.delay(16)
            }
        }
    }
}
