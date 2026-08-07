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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

/** Height of the compose row, both idle (icon pill) and expanded (text field + send button). */
private val ComposeBarHeight = 56.dp

/** Horizontal padding the message list keeps on top of the scaffold's own content padding. */
private val ListHorizontalPadding = 8.dp

/**
 * Extra horizontal inset that keeps the bottom-most compose row inside a round display.
 *
 * The row sits just above the screen's bottom content inset, and that far down a circular screen the
 * visible width is a chord rather than the full screen width - a full-width row would have its
 * corners (the send button, the left end of the text field) swallowed by the bezel. This solves for
 * the widest fully-rounded pill of [barHeight] whose bottom corner arcs still fit inside the display
 * circle, then returns however much more padding that needs than the [appliedHorizontal] the list
 * already keeps on each side.
 */
@Composable
private fun composeBarExtraInset(barHeight: Dp, bottomInset: Dp, appliedHorizontal: Dp): Dp {
    val configuration = LocalConfiguration.current
    if (!configuration.isScreenRound) return 0.dp
    val halfScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) / 2f
    val cornerRadius = barHeight.value / 2f
    // The centre of the row's bottom corner arc has to stay within (radius - cornerRadius) of the
    // screen centre for the whole arc to clear the bezel.
    val limit = halfScreen - cornerRadius
    val arcCentreY = halfScreen - bottomInset.value - cornerRadius
    val safeHalfWidth = cornerRadius + sqrt((limit * limit - arcCentreY * arcCentreY).coerceAtLeast(0f))
    val required = (configuration.screenWidthDp / 2f) - safeHalfWidth
    return (required.dp - appliedHorizontal).coerceAtLeast(0.dp)
}

@OptIn(ExperimentalLayoutApi::class)
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
    val focusManager = LocalFocusManager.current

    fun stopComposing() {
        composing = false
        keyboardController?.hide()
        // The text field stays mounted (see MessageComposeBar), so focus has to be handed back
        // explicitly or it would keep holding it - and keep the keyboard one tap away from
        // reopening - while the icon row is showing.
        focusManager.clearFocus()
    }

    // The IME pushes one last editor update as its session is torn down, and it still carries the
    // text that was in the field. After a send that update lands a few milliseconds *after* the
    // field was cleared, putting the just-sent message straight back into the input - so the next
    // tap on the keyboard icon would reopen on the previous message. Updates are only from the user
    // while the compose row is actually open, so anything arriving after it closed is dropped.
    fun onInputChange(value: TextFieldValue) {
        if (composing) {
            input = value
        }
    }

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

    // Whether the keyboard is currently up. Two signals are watched because a Wear IME either
    // resizes the window (reporting an ime inset) or takes it over full-screen (taking window focus
    // away from the app), depending on the device's keyboard.
    val keyboardShowing = rememberUpdatedState(WindowInsets.isImeVisible || !LocalWindowInfo.current.isWindowFocused)

    // Consume back presses while composing so they walk back out of the compose row one step at a
    // time - keyboard first, then the row itself - instead of falling through to the outer
    // navigation back handler, which would exit the chat entirely. Any typed text is kept, so
    // reopening the keyboard picks the draft back up.
    BackHandler(enabled = composing) {
        if (keyboardShowing.value) {
            keyboardController?.hide()
        } else {
            stopComposing()
        }
    }

    // Dismissing the keyboard with its own close button only hides the IME - it leaves the text
    // field focused - so without this the compose row would stay expanded over an empty input with
    // no way back to the icon row. Wait for the keyboard to actually come up, then collapse as soon
    // as it goes away again with nothing typed (with a draft typed the row stays, so it can still
    // be sent).
    LaunchedEffect(composing) {
        if (!composing) return@LaunchedEffect
        snapshotFlow { keyboardShowing.value }.first { it }
        snapshotFlow { keyboardShowing.value }.first { !it }
        if (input.text.isBlank()) {
            stopComposing()
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
                val composeBarInset = composeBarExtraInset(
                    barHeight = ComposeBarHeight,
                    bottomInset = contentPadding.calculateBottomPadding(),
                    appliedHorizontal = contentPadding.calculateStartPadding(LocalLayoutDirection.current) + ListHorizontalPadding,
                )
                Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
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
                            .padding(horizontal = ListHorizontalPadding + composeBarInset, vertical = 4.dp),
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
                            onValueChange = ::onInputChange,
                            focusRequester = focusRequester,
                            onDismissEmpty = ::stopComposing,
                            onSend = {
                                if (input.text.isNotBlank()) {
                                    onSend(input.text)
                                    input = TextFieldValue("")
                                }
                                stopComposing()
                            },
                        )
                    }
                }
            }
        } else {
            ScreenScaffold(scrollState = listState) { contentPadding ->
                val composeBarInset = composeBarExtraInset(
                    barHeight = ComposeBarHeight,
                    bottomInset = contentPadding.calculateBottomPadding(),
                    appliedHorizontal = contentPadding.calculateStartPadding(LocalLayoutDirection.current) + ListHorizontalPadding,
                )
                val roundScreen = LocalConfiguration.current.isScreenRound
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
                        // Messages scroll under the top of the content area, and on a round watch that
                        // corner of the screen curves away - a right- or left-aligned bubble reaching
                        // it would spill past the bezel. Clipping to the display's own circle keeps
                        // every pixel the list draws inside the physical screen.
                        .then(if (roundScreen) Modifier.clip(CircleShape) else Modifier)
                        .padding(contentPadding)
                        .rotaryScrollable(
                            behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                            focusRequester = rotaryFocusRequester,
                        )
                        .focusRequester(rotaryFocusRequester)
                        .focusable(),
                    contentPadding = PaddingValues(horizontal = ListHorizontalPadding, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = composeBarInset)) {
                            MessageComposeBar(
                                composing = composing,
                                onMic = ::startVoiceInput,
                                onKeyboard = ::startComposing,
                                onStickers = {
                                    onOpenStickerPicker()
                                    showStickerPicker = true
                                },
                                value = input,
                                onValueChange = ::onInputChange,
                                focusRequester = focusRequester,
                                onDismissEmpty = ::stopComposing,
                                onSend = {
                                    if (input.text.isNotBlank()) {
                                        onSend(input.text)
                                        input = TextFieldValue("")
                                    }
                                    stopComposing()
                                },
                            )
                        }
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
                ComposeRowButton(icon = Icons.Outlined.InsertEmoticon, description = "Stickers", onClick = onStickers)
                ComposeRowButton(icon = Icons.Outlined.Mic, description = "Voice input", onClick = onMic)
                ComposeRowButton(icon = Icons.Outlined.Keyboard, description = "Keyboard", onClick = onKeyboard)
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
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
        }
    }
}

@Composable
private fun ComposeRowButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description)
    }
}
