package com.nanchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
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
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
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
import com.nanchat.app.network.OwnProfile
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
    ownProfile: OwnProfile?,
    myAddress: String,
    onOpenChat: (Chat) -> Unit,
    onNewChat: () -> Unit,
    onRetry: () -> Unit,
    onLongPressChat: (Chat) -> Unit,
    onShowSeed: () -> Unit,
    onShowBlockedAndHidden: () -> Unit,
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
                        myAddress = myAddress,
                        onOpenChat = onOpenChat,
                        onNewChat = onNewChat,
                        onRetry = onRetry,
                        onLongPressChat = onLongPressChat,
                    )
                } else {
                    SettingsPage(
                        appVersion = appVersion,
                        ownProfile = ownProfile,
                        isActive = pagerState.currentPage == page,
                        onShowSeed = onShowSeed,
                        onShowBlockedAndHidden = onShowBlockedAndHidden,
                        onLogout = onLogout,
                    )
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
    myAddress: String,
    onOpenChat: (Chat) -> Unit,
    onNewChat: () -> Unit,
    onRetry: () -> Unit,
    onLongPressChat: (Chat) -> Unit,
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
                        ChatRow(
                            chat = chat,
                            isPendingRequest = chat.isPendingRequest(myAddress),
                            transformationSpec = transformationSpec,
                            onClick = { onOpenChat(chat) },
                            onLongClick = { onLongPressChat(chat) },
                        )
                    }
                }
            }
        }
    }
}

/** Full-screen action menu for a long-pressed chat - mute/unmute, hide (local to this device, see
 * [com.nanchat.app.data.ChatRepository.hideChat]), or block (removes the chat and stops the other
 * person messaging again). Its own screen (reached via [com.nanchat.app.presentation.MainActivity]'s
 * nav, dismissed by swipe/back like every other sub-screen) rather than an overlay, so it gets the
 * same scrollable-with-rotary + native scroll indicator + top-safe-area handling as the rest of the
 * app instead of reinventing it. */
@Composable
fun ChatActionMenu(
    chat: Chat,
    onToggleMute: () -> Unit,
    onHide: () -> Unit,
    onBlock: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val rotaryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocusRequester.requestFocus() }

    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState)
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(scrollableState = scrollState),
                        focusRequester = rotaryFocusRequester,
                    )
                    .focusRequester(rotaryFocusRequester)
                    .focusable(),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = chat.otherName?.takeIf { it.isNotBlank() } ?: shortAddress(chat.otherAccount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onToggleMute,
                    icon = { Icon(Icons.Outlined.NotificationsOff, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text(if (chat.muted) "Unmute" else "Mute", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onHide,
                    icon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text("Hide", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBlock,
                    icon = { Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text("Block", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SettingsPage(
    appVersion: String,
    ownProfile: OwnProfile?,
    isActive: Boolean,
    onShowSeed: () -> Unit,
    onShowBlockedAndHidden: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmingLogout by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    // verticalScroll (unlike TransformingLazyColumn on the chat list) has no built-in rotary
    // (crown/bezel) support - it only delivers scroll events to a focused element. Both pager pages
    // are composed up front, so requesting focus once at first composition (LaunchedEffect(Unit))
    // fires before this page is ever the visible one and loses to the chat list page's own focus -
    // request it every time this page actually becomes the selected one instead.
    val rotaryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isActive) { if (isActive) rotaryFocusRequester.requestFocus() }
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
                text = "Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            if (ownProfile != null) {
                OwnProfileCard(ownProfile)
                Spacer(Modifier.height(14.dp))
            }
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
                onClick = onShowBlockedAndHidden,
                icon = { Icon(Icons.Outlined.PeopleOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
            ) {
                Text("Blocked", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** The signed-in account's own name + NanChat ID (see [OwnProfile]/`/account`'s `username` field,
 * MyAccount.tsx's "NanChat ID" on the phone app) with avatar, at the top of Settings. */
@Composable
private fun OwnProfileCard(profile: OwnProfile) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (profile.avatarUrl != null) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (profile.name?.takeIf { it.isNotBlank() } ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name?.takeIf { it.isNotBlank() } ?: "NanChat user",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile.username != null) {
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.ChatRow(
    chat: Chat,
    isPendingRequest: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(chat)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.otherName?.takeIf { it.isNotBlank() } ?: shortAddress(chat.otherAccount),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat.muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.NotificationsOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(
                    text = if (isPendingRequest) "Message request" else chat.lastMessagePreview ?: "New chat",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPendingRequest) MaterialTheme.colorScheme.primary else Color.Unspecified,
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
