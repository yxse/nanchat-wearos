package com.nanchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/** What [ConfirmReversalScreen] is reversing - either an unblock or an unhide, carrying enough to
 * both display who it's about and perform the action once confirmed. */
sealed interface PendingReversal {
    data class Unblock(val address: String, val name: String) : PendingReversal
    data class Unhide(val chatId: String, val name: String) : PendingReversal
}

/**
 * A single confirm-or-swipe-back screen for reversing a block/hide decision, reached by tapping a
 * name in [BlockedHiddenScreen]. No inline Confirm/Cancel row and no explicit Cancel button here -
 * swiping back (via [com.nanchat.app.presentation.MainActivity]'s SwipeToDismissBox, the same as
 * every other sub-screen in the app) is the cancel action.
 */
@Composable
fun ConfirmReversalScreen(target: PendingReversal, onConfirm: () -> Unit) {
    val (icon, verb, detail, name) = when (target) {
        is PendingReversal.Unblock -> ReversalCopy(
            Icons.Outlined.LockOpen,
            "Unblock",
            "They'll be able to message you again.",
            target.name,
        )
        is PendingReversal.Unhide -> ReversalCopy(
            Icons.Outlined.Visibility,
            "Unhide",
            "This chat will show up in your list again.",
            target.name,
        )
    }

    val scrollState = rememberScrollState()
    val rotaryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocusRequester.requestFocus() }

    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(scrollableState = scrollState),
                        focusRequester = rotaryFocusRequester,
                    )
                    .focusRequester(rotaryFocusRequester)
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "$verb $name?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = onConfirm) {
                    Text("Confirm")
                }
            }
        }
    }
}

private data class ReversalCopy(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val verb: String,
    val detail: String,
    val name: String,
)
