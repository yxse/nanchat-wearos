package com.nanchat.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nanchat.app.data.Message
import com.nanchat.app.presentation.theme.NanChatTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.hypot

/**
 * Play's Wear OS visual quality bar requires that nothing the app draws falls outside the physical
 * display, which on a round watch is the circle inscribed in the (square) screen - anything painted
 * in the corners is hidden behind the bezel and reads as cut off. These tests render the chat screen
 * and fail if any pixel outside that circle was painted over the background.
 */
@RunWith(AndroidJUnit4::class)
class ChatScreenLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun message(id: String, text: String, mine: Boolean) = Message(
        id = id,
        chatId = "chat",
        contentEncrypted = "",
        fromAccount = if (mine) ME else "nano_them",
        toAccount = if (mine) "nano_them" else ME,
        timestamp = "2026-01-01T00:00:00Z",
        height = 0,
        decrypted = text,
    )

    private fun setChatContent(messages: List<Message>) {
        rule.setContent {
            NanChatTheme {
                ChatScreen(
                    messages = messages,
                    loading = false,
                    loadingOlder = false,
                    myAddress = ME,
                    onSend = {},
                    onLoadOlderMessages = {},
                    stickerCollections = emptyList(),
                    onOpenStickerPicker = {},
                    onLoadStickers = { _, _ -> },
                    onSendSticker = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun conversationStaysInsideTheRoundDisplay() {
        setChatContent(
            listOf(
                message("1", "hello", mine = true),
                message("2", "Hello hello", mine = false),
                message("3", "Haka", mine = true),
                message("4", "One more message so the list overflows the screen", mine = false),
            )
        )
        assertNothingOutsideDisplay("chat-messages")
    }

    @Test
    fun emptyConversationStaysInsideTheRoundDisplay() {
        setChatContent(emptyList())
        assertNothingOutsideDisplay("chat-empty")
    }

    @Test
    fun composeRowStaysInsideTheRoundDisplay() {
        setChatContent(
            listOf(
                message("1", "hello", mine = true),
                message("2", "Hello hello", mine = false),
            )
        )
        rule.onNodeWithContentDescription("Keyboard").performClick()
        rule.waitForIdle()
        assertNothingOutsideDisplay("chat-composing")
    }

    /**
     * Captures the screen and checks every pixel that sits outside the display circle still holds the
     * background colour. A handful of pixels are tolerated for anti-aliasing along the boundary.
     */
    private fun assertNothingOutsideDisplay(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        saveForInspection(bitmap, name)

        val isRound = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .configuration.isScreenRound
        if (!isRound) return

        val centreX = bitmap.width / 2f
        val centreY = bitmap.height / 2f
        val radius = minOf(centreX, centreY)
        val background = bitmap.getPixel(1, 1)
        var outside = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                // Half a pixel of slack: the corner-most pixel of a shape that ends exactly on the
                // circle is still on the display.
                if (hypot(x + 0.5f - centreX, y + 0.5f - centreY) <= radius + 0.5f) continue
                if (bitmap.getPixel(x, y) != background) outside++
            }
        }
        assertTrue(
            "$name: $outside pixels are painted outside the round display area (see the saved screenshot)",
            outside <= 32,
        )
    }

    private fun saveForInspection(bitmap: Bitmap, name: String) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val ME = "nano_me"
    }
}
