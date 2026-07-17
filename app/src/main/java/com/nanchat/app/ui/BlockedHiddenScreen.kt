package com.nanchat.app.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.nanchat.app.data.BlockedAccount
import com.nanchat.app.data.Chat
import com.nanchat.app.ui.util.shortAddress

/** Lists accounts blocked (`/block-chat`) and chats hidden on this device ([SecureStore.hiddenChatIds])
 * side by side, each with a one-tap way back - the only place either decision can be reversed from. */
@Composable
fun BlockedHiddenScreen(
    blockedAccounts: List<BlockedAccount>,
    hiddenChats: List<Chat>,
    onUnblock: (String) -> Unit,
    onUnhide: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val rotaryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocusRequester.requestFocus() }
    // Reversing either decision needs a second tap to confirm - only one row (across both sections)
    // asks for confirmation at a time.
    var confirmingBlocked by remember { mutableStateOf<String?>(null) }
    var confirmingHidden by remember { mutableStateOf<String?>(null) }

    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 14.dp)
                    .verticalScroll(scrollState)
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(scrollableState = scrollState),
                        focusRequester = rotaryFocusRequester,
                    )
                    .focusRequester(rotaryFocusRequester)
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Blocked & hidden",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                SectionLabel("Blocked")
                if (blockedAccounts.isEmpty()) {
                    EmptyRow("No blocked accounts")
                } else {
                    blockedAccounts.forEach { account ->
                        NameRow(
                            name = account.name?.takeIf { it.isNotBlank() } ?: shortAddress(account.address),
                            actionLabel = "Unblock",
                            confirming = confirmingBlocked == account.address,
                            onRequestConfirm = { confirmingBlocked = account.address },
                            onConfirm = { confirmingBlocked = null; onUnblock(account.address) },
                            onCancel = { confirmingBlocked = null },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Hidden")
                if (hiddenChats.isEmpty()) {
                    EmptyRow("No hidden chats")
                } else {
                    hiddenChats.forEach { chat ->
                        NameRow(
                            name = chat.otherName?.takeIf { it.isNotBlank() } ?: shortAddress(chat.otherAccount),
                            actionLabel = "Unhide",
                            confirming = confirmingHidden == chat.id,
                            onRequestConfirm = { confirmingHidden = chat.id },
                            onConfirm = { confirmingHidden = null; onUnhide(chat.id) },
                            onCancel = { confirmingHidden = null },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyExtraSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NameRow(
    name: String,
    actionLabel: String,
    confirming: Boolean,
    onRequestConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    // The card itself isn't a click target (onClick is a no-op) - only the action/confirm/cancel
    // text buttons are, so a stray tap on the name never accidentally reverses the decision.
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        if (confirming) {
            // Confirm+Cancel don't fit next to the name on one line at this screen width - stack them
            // below it instead of squeezing the name down to nothing (see the mute/hide/block menu's
            // width lessons).
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onConfirm) {
                        Text("Confirm?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = onRequestConfirm) {
                    Text(actionLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
