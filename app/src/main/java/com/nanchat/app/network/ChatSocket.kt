package com.nanchat.app.network

import com.nanchat.app.data.Message
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.time.Instant

/** Thin wrapper around the Socket.IO connection used for realtime message send/receive. */
class ChatSocket(private val baseUrl: String = "https://api.nanchat.com") {

    private var socket: Socket? = null

    fun connect(token: String, myAddress: String, onMessage: (Message) -> Unit, onConnected: () -> Unit) {
        disconnect()
        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            reconnection = true
        }
        val s = IO.socket(baseUrl, options)
        s.on(Socket.EVENT_CONNECT) {
            s.emit("join", myAddress)
            onConnected()
        }
        s.on("message") { args ->
            val obj = args.getOrNull(0) as? JSONObject ?: return@on
            onMessage(
                Message(
                    id = obj.optString("_id"),
                    chatId = obj.optString("chatId"),
                    contentEncrypted = obj.optString("content"),
                    fromAccount = obj.optString("fromAccount"),
                    toAccount = obj.optString("toAccount"),
                    timestamp = obj.optString("timestamp"),
                    height = obj.optLong("height"),
                    stickerUrl = if (obj.isNull("stickerId")) null else obj.optString("stickerId").ifEmpty { null },
                ),
            )
        }
        socket = s
        s.connect()
    }

    fun sendMessage(
        chatId: String,
        encryptedContent: String,
        toAccount: String,
        fromAccount: String,
        onAck: (success: Boolean, error: String?) -> Unit,
    ) {
        val currentSocket = socket
        if (currentSocket == null) {
            onAck(false, "Not connected")
            return
        }
        val payload = JSONObject()
            .put("chatId", chatId)
            .put("content", encryptedContent)
            .put("toAccount", toAccount)
            .put("fromAccount", fromAccount)
            .put("timestamp", Instant.now().toString())
        currentSocket.emit(
            "message",
            payload,
            Ack { args ->
                val response = args.getOrNull(0) as? JSONObject
                onAck(response?.optBoolean("success", false) ?: false, response?.optString("error"))
            },
        )
    }

    fun sendSticker(
        chatId: String,
        stickerUrl: String,
        toAccount: String,
        fromAccount: String,
        onAck: (success: Boolean, error: String?) -> Unit,
    ) {
        val currentSocket = socket
        if (currentSocket == null) {
            onAck(false, "Not connected")
            return
        }
        // Sticker messages carry a plain, unencrypted "Sticker" placeholder as content (mirrors the
        // web/phone app - see ChatInputMessage.tsx sendStickerMessage) plus the sticker's image URL.
        val payload = JSONObject()
            .put("chatId", chatId)
            .put("content", "Sticker")
            .put("stickerId", stickerUrl)
            .put("toAccount", toAccount)
            .put("fromAccount", fromAccount)
            .put("timestamp", Instant.now().toString())
        currentSocket.emit(
            "message",
            payload,
            Ack { args ->
                val response = args.getOrNull(0) as? JSONObject
                onAck(response?.optBoolean("success", false) ?: false, response?.optString("error"))
            },
        )
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }
}
