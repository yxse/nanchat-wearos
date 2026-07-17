package com.nanchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import com.nanchat.app.data.Chat
import com.nanchat.app.ui.util.shortAddress

/** The chat list is page 0 of a 2-page pager; swiping right reveals Settings (page 1) - show
 * recovery phrase, log out, and the installed app version. */
@Composable
fun ChatListScreen(
    chats: List<Chat>,
    loading: Boolean,
    error: String?,
    hiddenGroupCount: Int,
    appVersion: String,
    onOpenChat: (Chat) -> Unit,
    onNewChat: () -> Unit,
    onRetry: () -> Unit,
    onShowSeed: () -> Unit,
    onLogout: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    AppScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    ChatListPage(
                        chats = chats,
                        loading = loading,
                        error = error,
                        hiddenGroupCount = hiddenGroupCount,
                        onOpenChat = onOpenChat,
                        onNewChat = onNewChat,
                        onRetry = onRetry,
                    )
                } else {
                    SettingsPage(appVersion = appVersion, onShowSeed = onShowSeed, onLogout = onLogout)
                }
            }
            // Native Wear page dots (same component the system uses, e.g. the media control app) -
            // the only hint that Settings is a swipe away, since nothing else on the chat list says so.
            HorizontalPageIndicator(pagerState = pagerState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ChatListPage(
    chats: List<Chat>,
    loading: Boolean,
    error: String?,
    hiddenGroupCount: Int,
    onOpenChat: (Chat) -> Unit,
    onNewChat: () -> Unit,
    onRetry: () -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(state) { contentPadding ->
        // On the very first load (no chats known yet), show only the loading indicator - not the
        // "Start chat" button or list, which would otherwise flash before we know what's there.
        if (loading && chats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            TransformingLazyColumn(
                state = state,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Button(
                        onClick = onNewChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .scale(0.85f),
                        transformation = SurfaceTransformation(transformationSpec),
                        icon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
                    ) {
                        Text("Start chat")
                    }
                }
                if (chats.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            if (error != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = onRetry) {
                                        Text("Retry")
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "No conversations", style = MaterialTheme.typography.bodySmall)
                                    if (hiddenGroupCount > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "$hiddenGroupCount group chat(s) hidden",
                                            style = MaterialTheme.typography.bodyExtraSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(chats, key = { it.id }) { chat ->
                        ChatRow(chat = chat, transformationSpec = transformationSpec, onClick = { onOpenChat(chat) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(appVersion: String, onShowSeed: () -> Unit, onLogout: () -> Unit) {
    var confirmingLogout by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 14.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onShowSeed,
                icon = { Icon(Icons.Outlined.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp)) },
            ) {
                Text("Recovery", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (confirmingLogout) onLogout() else confirmingLogout = true },
                icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(16.dp)) },
            ) {
                Text(
                    if (confirmingLogout) "Confirm" else "Log out",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (confirmingLogout) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "This removes your account from this watch. Make sure you've backed up your recovery phrase first.",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { confirmingLogout = false }) {
                    Text("Cancel")
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "NanChat v$appVersion",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.ChatRow(
    chat: Chat,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(chat)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.otherName?.takeIf { it.isNotBlank() } ?: shortAddress(chat.otherAccount),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    text = chat.lastMessagePreview ?: "New chat",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            if (chat.unreadCount > 0) {
                // Tertiary (not primary) per the Wear color guide: a badge that should draw attention
                // without competing with primary actions like the "Start chat" button.
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }
        }
    }
}

@Composable
private fun ChatAvatar(chat: Chat) {
    if (chat.otherAvatarUrl != null) {
        AsyncImage(
            model = chat.otherAvatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (chat.otherName?.takeIf { it.isNotBlank() } ?: chat.otherAccount).take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
