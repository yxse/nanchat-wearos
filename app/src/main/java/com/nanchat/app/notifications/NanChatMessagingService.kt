package com.nanchat.app.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nanchat.app.crypto.NanoCrypto
import com.nanchat.app.data.SecureStore
import com.nanchat.app.network.ChatApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives push notifications delivered via Firebase Cloud Messaging - this is what lets a
 * message notification show up even if the app process (and its Socket.IO connection, see
 * ChatRepository) has been killed by the system. Registration happens once per login
 * (ChatRepository.registerPushToken) and again here whenever FCM rotates the token.
 *
 * Mirrors the backend contract used by the phone app (NanChat/src/nano/notifications.ts,
 * nanwallet-backend/server_notifications.js) - unauthenticated `POST /notifications/push/subscribe`
 * to register, and a data-only payload (chatId/fromAccount/fromAccountName/...) to receive.
 * The notification never shows message content, even when it can be decrypted - only that a
 * message arrived and from whom - so a locked watch screen never leaks a conversation's contents.
 */
class NanChatMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        registerToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // The live socket connection (ChatRepository) already delivers + notifies for this while
        // the app is on screen; avoid a duplicate notification for the same message.
        if (AppForeground.inForeground) return

        val data = remoteMessage.data
        val chatId = data["chatId"] ?: remoteMessage.messageId ?: return
        // Muted/blocked/unaccepted chats are already excluded server-side before this push is even
        // sent (sendPushNewMessage in server_notifications.js) - but "hidden" is a watch-only concept
        // the server doesn't know about, so it's the one case that still needs checking here.
        if (chatId in SecureStore(applicationContext).hiddenChatIds) return
        val fromAccount = data["fromAccount"]
        val title = data["fromAccountName"]?.takeIf { it.isNotBlank() }
            ?: fromAccount
            ?: remoteMessage.notification?.title
            ?: "NanChat"
        // Content is end-to-end encrypted - the notification only announces that a message arrived
        // and from whom, never what it says (no decryption happens here anymore, by design).
        NotificationHelper.notifyMessage(applicationContext, chatId, title, "New message")
    }

    companion object {
        private const val TAG = "NanChatMessagingSvc"

        fun registerToken(context: Context, token: String) {
            val seed = SecureStore(context).seed ?: return
            val address = NanoCrypto.deriveAccount(seed).address
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ChatApi().registerPushToken(token, address)
                } catch (e: Exception) {
                    Log.e(TAG, "registerToken failed", e)
                }
            }
        }
    }
}
