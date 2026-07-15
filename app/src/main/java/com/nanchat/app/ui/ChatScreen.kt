package com.nanchat.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.InsertEmoticon
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.nanchat.app.data.Message
import com.nanchat.app.data.Sticker
import com.nanchat.app.data.StickerCollection

/**
 * ScreenScaffold's default contentPadding reserves ~10% of the screen height at both the top and
 * bottom (see PaddingDefaults.verticalContentPaddingPercentage in Wear Compose Material3) to keep
 * content clear of the round bezel. That's right for most screens, but it leaves a very visible gap
 * between the compose row and the true bottom of the watch face - the compose row is already a
 * narrow pill shape that reads fine much closer to the edge, so its bottom inset is shrunk here.
 */
@Composable
private fun PaddingValues.withComposeBarBottomInset(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = 4.dp,
    )
}

@Composable
fun ChatScreen(
    messages: List<Message>,
    loading: Boolean,
    loadingOlder: Boolean,
    myAddress: String,
    onSend: (String) -> Unit,
    onLoadOlderMessages: () -> Unit,
    stickerCollections: List<StickerCollection>,
    onOpenStickerPicker: () -> Unit,
    onLoadStickers: (collectionId: String, onResult: (List<Sticker>) -> Unit) -> Unit,
    onSendSticker: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var composing by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val rotaryFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (showStickerPicker) {
        StickerPickerScreen(
            collections = stickerCollections,
            onLoadStickers = onLoadStickers,
            onStickerSelected = { url ->
                onSendSticker(url)
                showStickerPicker = false
            },
            onClose = { showStickerPicker = false },
        )
        return
    }

    // Fetching + decrypting the conversation's messages takes a moment (native NaCl-box calls per
    // message); show the M3 Expressive loading indicator instead of an empty screen for that span.
    if (loading && messages.isEmpty()) {
        AppScaffold {
            ScreenScaffold { contentPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        return
    }

    // Rotary (crown/bezel) scroll only delivers events to a focused element, so the list needs to
    // hold focus by default and reclaim it whenever the compose text field gives focus back up.
    LaunchedEffect(composing) {
        if (!composing) {
            rotaryFocusRequester.requestFocus()
        }
    }

    // The list uses reverseLayout (see below) so the conversation anchors to the bottom of the screen
    // - right above the compose row - the way every chat app does, instead of messages piling up
    // against the top with empty space underneath when there are only one or two of them. With
    // reverseLayout, index 0 *is* the bottom, so keeping the latest message in view on arrival is
    // just scrolling back to index 0 (skipped while the user has scrolled up into history - keyed on
    // the newest message's id rather than messages.size so loading older messages, which appends at
    // the far end here, doesn't yank the view back down).
    val newestMessageId = messages.lastOrNull()?.id
    LaunchedEffect(newestMessageId, composing) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    // Reaching the top of the loaded history - the far end of the list under reverseLayout - requests
    // the next (older) page from the repository, which appends it there once decrypted.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index to info.totalItemsCount
        }.collect { (lastVisibleIndex, totalCount) ->
            if (lastVisibleIndex != null && totalCount > 0 && lastVisibleIndex >= totalCount - 3) {
                onLoadOlderMessages()
            }
        }
    }

    fun startComposing() {
        composing = true
    }

    // Consume back presses while composing to just collapse back to the icon row (when nothing was
    // typed), instead of letting them fall through to the outer navigation back handler, which
    // would exit the chat entirely.
    BackHandler(enabled = composing) {
        if (input.text.isBlank()) {
            composing = false
        } else {
            keyboardController?.hide()
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!recognized.isNullOrBlank()) {
            input = TextFieldValue(recognized)
            composing = true
        }
    }
    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // No speech recognizer available on this device; silently ignore.
        }
    }

    AppScaffold {
        if (messages.isEmpty()) {
            // Nothing to scroll through yet: the compose row is pinned to the bottom of the screen
            // instead of being a scrolling list item, with a centered hint that this is a brand new
            // conversation.
            ScreenScaffold { contentPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(contentPadding.withComposeBarBottomInset())) {
                    Text(
                        text = "No messages yet",
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        MessageComposeBar(
                            composing = composing,
                            onMic = ::startVoiceInput,
                            onKeyboard = ::startComposing,
                            onStickers = {
                                onOpenStickerPicker()
                                showStickerPicker = true
                            },
                            value = input,
                            onValueChange = { input = it },
                            focusRequester = focusRequester,
                            onDismissEmpty = { composing = false },
                            onSend = {
                                if (input.text.isNotBlank()) {
                                    onSend(input.text)
                                    input = TextFieldValue("")
                                }
                                composing = false
                                keyboardController?.hide()
                            },
                        )
                    }
                }
            }
        } else {
            ScreenScaffold(scrollState = listState) { contentPadding ->
                // reverseLayout anchors the conversation to the bottom of the screen (index 0 = right
                // above the compose row), the way every chat app lays out messages, instead of them
                // piling up against the top with dead space underneath when there are only one or two.
                // Item order follows: compose row (bottom) -> newest -> ... -> oldest -> loading-older
                // spinner (top), i.e. messages fed in newest-first. reverseLayout only flips the scroll
                // *direction*/item order though - it does NOT bias leftover space to an edge when the
                // conversation doesn't fill the screen, so verticalArrangement still needs an explicit
                // Alignment.Bottom or a short conversation ends up vertically centered instead.
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding.withComposeBarBottomInset())
                        .rotaryScrollable(
                            behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                            focusRequester = rotaryFocusRequester,
                        )
                        .focusRequester(rotaryFocusRequester)
                        .focusable(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    item {
                        MessageComposeBar(
                            composing = composing,
                            onMic = ::startVoiceInput,
                            onKeyboard = ::startComposing,
                            onStickers = {
                                onOpenStickerPicker()
                                showStickerPicker = true
                            },
                            value = input,
                            onValueChange = { input = it },
                            focusRequester = focusRequester,
                            onDismissEmpty = { composing = false },
                            onSend = {
                                if (input.text.isNotBlank()) {
                                    onSend(input.text)
                                    input = TextFieldValue("")
                                }
                                composing = false
                                keyboardController?.hide()
                            },
                        )
                    }
                    items(messages.asReversed(), key = { it.id }) { message ->
                        MessageBubble(message = message, isMine = message.fromAccount == myAddress)
                    }
                    if (loadingOlder) {
                        item(key = "loading-older") {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, indicatorSize = 20.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(composing) {
        if (composing) {
            // Requesting focus on the same frame the field enters composition sometimes binds a
            // dummy InputConnection (the real one isn't ready yet), silently dropping all typed
            // text. A couple of retries a frame apart reliably lands on the real one.
            repeat(5) {
                focusRequester.requestFocus()
                kotlinx.coroutines.delay(16)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        if (message.stickerUrl != null) {
            // Stickers render as a plain image with no bubble background, matching the web app's
            // MessageSticker component.
            AsyncImage(
                model = message.stickerUrl,
                contentDescription = null,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .background(
                        if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.decrypted ?: "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Idle state mirrors the system Google Messages reply row (stickers / mic / keyboard); tapping the
 * keyboard icon opens a real text field backed by the device's actual keyboard, which has its own
 * built-in emoji toggle - no need for a separate emoji button here. */
@Composable
private fun MessageComposeBar(
    composing: Boolean,
    onMic: () -> Unit,
    onKeyboard: () -> Unit,
    onStickers: () -> Unit,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onDismissEmpty: () -> Unit,
    onSend: () -> Unit,
) {
    // The field's very first focus state after entering composition is "unfocused" (focus is only
    // requested a moment later - see the retry loop in ChatScreen), so only treat it as a genuine
    // dismissal once it has actually held focus at least once.
    var hasFocusedOnce by remember(composing) { mutableStateOf(false) }
    if (!composing) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ComposeRowButton(icon = Icons.Outlined.InsertEmoticon, onClick = onStickers)
                ComposeRowButton(icon = Icons.Outlined.Mic, onClick = onMic)
                ComposeRowButton(icon = Icons.Outlined.Keyboard, onClick = onKeyboard)
            }
        }
    }
    // Stays mounted at all times (just zero-sized when idle) rather than being added/removed from
    // composition on demand - creating the field fresh right as focus is requested races Compose's
    // text-input registration on this device and silently binds a dummy InputConnection, dropping
    // every keystroke.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (composing) Modifier.padding(bottom = 4.dp) else Modifier.size(0.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodySmall.fontSize),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hasFocusedOnce = true
                    } else if (composing && hasFocusedOnce && value.text.isBlank()) {
                        onDismissEmpty()
                    }
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onSend) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
        }
    }
}

@Composable
private fun ComposeRowButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null)
    }
}
