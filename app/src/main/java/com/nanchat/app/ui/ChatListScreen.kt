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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import com.nanchat.app.data.Chat
import com.nanchat.app.ui.util.shortAddress

@Composable
fun ChatListScreen(
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
    AppScaffold {
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
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
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
