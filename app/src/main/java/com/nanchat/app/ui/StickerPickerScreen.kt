package com.nanchat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.wear.compose.material3.CircularProgressIndicator
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

    val gridState = rememberLazyGridState()
    val rotaryFocusRequester = remember { FocusRequester() }
    // The grid only enters composition once stickers are loaded, so requesting focus once at
    // startup would target a focusRequester with nothing attached yet - retry once it's shown.
    LaunchedEffect(gridShown) {
        if (gridShown) rotaryFocusRequester.requestFocus()
    }

    AppScaffold {
        ScreenScaffold { contentPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                if (collections.isEmpty()) {
                    Text(
                        text = "No sticker packs yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (!gridShown) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .rotaryScrollable(
                                behavior = RotaryScrollableDefaults.behavior(scrollableState = gridState),
                                focusRequester = rotaryFocusRequester,
                            )
                            .focusRequester(rotaryFocusRequester)
                            .focusable(),
                    ) {
                        gridItems(stickers, key = { it.id }) { sticker ->
                            AsyncImage(
                                model = sticker.url,
                                contentDescription = sticker.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onStickerSelected(sticker.url) },
                            )
                        }
                    }
                }
            }
        }
    }
}
