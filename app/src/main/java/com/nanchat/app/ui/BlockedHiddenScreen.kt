package com.nanchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.nanchat.app.data.BlockedAccount
import com.nanchat.app.data.Chat
import com.nanchat.app.ui.util.shortAddress

/** Lists accounts blocked (`/block-chat`) and chats hidden on this device ([SecureStore.hiddenChatIds])
 * side by side. Tapping a name is the only way back - it opens [ConfirmReversalScreen] rather than
 * an inline confirm row, matching the rest of the app's "tap for a dedicated confirm screen, swipe
 * back to cancel" pattern instead of an explicit Cancel button. */
@Composable
fun BlockedHiddenScreen(
    blockedAccounts: List<BlockedAccount>,
    hiddenChats: List<Chat>,
    onRequestUnblock: (BlockedAccount) -> Unit,
    onRequestUnhide: (Chat) -> Unit,
) {
    val scrollState = rememberScrollState()
    val rotaryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocusRequester.requestFocus() }

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
                            avatarUrl = account.avatarUrl,
                            onClick = { onRequestUnblock(account) },
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
                            avatarUrl = chat.otherAvatarUrl,
                            onClick = { onRequestUnhide(chat) },
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
private fun NameRow(name: String, avatarUrl: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RowAvatar(avatarUrl = avatarUrl, fallbackText = name)
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** Same small-avatar-with-initial-fallback treatment as the chat list's own avatar ([ChatAvatar] in
 * ChatListScreen.kt) - not shared directly since that one is private and keyed on a [Chat], while
 * this needs to work for both a [Chat] (hidden) and a [BlockedAccount] (blocked). */
@Composable
private fun RowAvatar(avatarUrl: String?, fallbackText: String) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(28.dp).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallbackText.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
