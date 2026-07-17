package com.nanchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.nanchat.app.data.Chat
import com.nanchat.app.ui.util.shortAddress

/**
 * Shown instead of the normal conversation when opening a chat someone else started that we
 * haven't accepted or hidden yet (see [Chat.isPendingRequest]) - matches the phone app's
 * "message request" banner (NewMessageWarning.tsx), but as its own screen rather than a banner
 * above the composer: our compose bar already fills most of the small round screen, and unlike the
 * phone app we haven't accepted-loaded the conversation yet, so there's nothing to show behind it.
 *
 * Unlike the phone app (whose banner's reject action blocks), refusing here hides the chat instead
 * (see [com.nanchat.app.data.ChatRepository.hideChat]) - reversible from the Settings "Blocked &
 * hidden" screen, and it stops notifications without preventing the sender from ever reaching us
 * again the way blocking does.
 */
@Composable
fun MessageRequestScreen(chat: Chat, onAccept: () -> Unit, onRefuse: () -> Unit) {
    val scrollState = rememberScrollState()
    val rotaryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocusRequester.requestFocus() }

    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(scrollableState = scrollState),
                        focusRequester = rotaryFocusRequester,
                    )
                    .focusRequester(rotaryFocusRequester)
                    .focusable(),
            ) {
                Spacer(Modifier.height(4.dp))
                if (chat.otherAvatarUrl != null) {
                    AsyncImage(
                        model = chat.otherAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (chat.otherName?.takeIf { it.isNotBlank() } ?: chat.otherAccount).take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = chat.otherName?.takeIf { it.isNotBlank() } ?: shortAddress(chat.otherAccount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Message request",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (chat.lastMessagePreview != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = chat.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAccept,
                    icon = { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text("Accept", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRefuse,
                    icon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text("Refuse", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
