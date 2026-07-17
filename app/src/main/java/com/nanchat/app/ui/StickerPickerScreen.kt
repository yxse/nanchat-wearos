package com.nanchat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.nanchat.app.data.Sticker
import com.nanchat.app.data.StickerCollection

/** Wear-sized sticker picker: all stickers from all of the account's collections flattened into a
 * single scrollable grid (no per-collection navigation), scrollable with the rotary crown. Mirrors
 * the web app's sticker picker (NanChat/src/components/messaging/components/), trimmed down for a
 * round, low-real-estate screen. */
@Composable
fun StickerPickerScreen(
    collections: List<StickerCollection>,
    onLoadStickers: (collectionId: String, onResult: (List<Sticker>) -> Unit) -> Unit,
    onStickerSelected: (url: String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    var stickersByCollection by remember { mutableStateOf<Map<String, List<Sticker>>>(emptyMap()) }
    LaunchedEffect(collections) {
        collections.forEach { col ->
            onLoadStickers(col.id) { loaded ->
                stickersByCollection = stickersByCollection + (col.id to loaded)
            }
        }
    }

    val allLoaded = collections.isNotEmpty() && collections.all { stickersByCollection.containsKey(it.id) }
    val stickers = collections.flatMap { stickersByCollection[it.id].orEmpty() }
    val gridShown = collections.isNotEmpty() && (allLoaded || stickers.isNotEmpty())
    // Rows of 3 rather than LazyVerticalGrid: Wear Material3's ScreenScaffold/ScrollIndicator only
    // wire up automatically from a LazyListState, so a plain LazyColumn is what gets the scrollbar.
    val stickerRows = stickers.chunked(3)

    val listState = rememberLazyListState()
    val rotaryFocusRequester = remember { FocusRequester() }
    // The grid only enters composition once stickers are loaded, so requesting focus once at
    // startup would target a focusRequester with nothing attached yet - retry once it's shown.
    LaunchedEffect(gridShown) {
        if (gridShown) rotaryFocusRequester.requestFocus()
    }

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                if (collections.isEmpty()) {
                    Text(
                        text = "No sticker packs yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (!gridShown) {
                    ExpressiveLoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .rotaryScrollable(
                                behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                                focusRequester = rotaryFocusRequester,
                            )
                            .focusRequester(rotaryFocusRequester)
                            .focusable(),
                    ) {
                        items(stickerRows, key = { row -> row.first().id }) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                row.forEach { sticker ->
                                    AsyncImage(
                                        model = sticker.url,
                                        contentDescription = sticker.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable { onStickerSelected(sticker.url) },
                                    )
                                }
                                repeat(3 - row.size) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
