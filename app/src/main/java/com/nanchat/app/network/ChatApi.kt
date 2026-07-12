package com.nanchat.app.network

import com.nanchat.app.data.Chat
import com.nanchat.app.data.Message
import com.nanchat.app.data.Sticker
import com.nanchat.app.data.StickerCollection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

data class ChatsResult(val privateChats: List<Chat>, val hiddenGroupCount: Int)

data class AccountLookupResult(val address: String, val name: String?, val avatarUrl: String?)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** Talks to the NanChat backend (https://api.nanchat.com), same API used by the phone app. */
class ChatApi(private val baseUrl: String = "https://api.nanchat.com") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetchToken(account: String, message: String, signature: String): Pair<String, Long> {
        val body = JSONObject()
            .put("account", account)
            .put("message", message)
            .put("signature", signature)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val json = executeJsonObject(Request.Builder().url("$baseUrl/token").post(body).build())
        if (!json.has("token")) throw ApiException(json.optString("error", "Login failed"))
        return json.getString("token") to json.optLong("expiresAt", 0L)
    }

    /** Returns private (1:1) chats only; group chats are ignored entirely. */
    fun fetchChats(token: String, myAddress: String): ChatsResult {
        val request = Request.Builder().url("$baseUrl/chats").header("token", token).build()
        val json = executeJsonObject(request)
        if (json.has("error")) throw ApiException(json.getString("error"))
        val chatsJson = json.optJSONArray("chats") ?: JSONArray()
        val chats = mutableListOf<Chat>()
        var hiddenGroupCount = 0
        for (i in 0 until chatsJson.length()) {
            val obj = chatsJson.getJSONObject(i)
            if (obj.optString("type", "private") == "group") {
                hiddenGroupCount++
            } else {
                parsePrivateChat(obj, myAddress)?.let { chats.add(it) }
            }
        }
        return ChatsResult(chats, hiddenGroupCount)
    }

    fun fetchMessages(token: String, chatId: String, cursor: Long, limit: Int): List<Message> {
        val request = Request.Builder()
            .url("$baseUrl/messages?chatId=$chatId&limit=$limit&cursor=$cursor")
            .header("token", token)
            .build()
        val arr = executeJsonArray(request)
        val messages = mutableListOf<Message>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            messages.add(
                Message(
                    id = m.optString("_id"),
                    chatId = m.optString("chatId"),
                    contentEncrypted = m.optString("content"),
                    fromAccount = m.optString("fromAccount"),
                    toAccount = m.optString("toAccount"),
                    timestamp = m.optString("timestamp"),
                    height = m.optLong("height"),
                    stickerUrl = if (m.isNull("stickerId")) null else m.optString("stickerId").ifEmpty { null },
                ),
            )
        }
        return messages
    }

    /** The sticker collections (packs) the signed-in account owns, same as `/stickers/me` on the phone app. */
    fun fetchStickerCollections(token: String): List<StickerCollection> {
        val request = Request.Builder().url("$baseUrl/stickers/me").header("token", token).build()
        val arr = executeJsonArray(request)
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            StickerCollection(id = c.optString("_id"), name = c.optString("name"), logo = c.optString("logo"))
        }
    }

    /** The stickers inside a single collection. */
    fun fetchStickers(collectionId: String): List<Sticker> {
        val request = Request.Builder().url("$baseUrl/stickers/$collectionId").build()
        val arr = executeJsonArray(request)
        return parseStickers(arr)
    }

    fun searchStickers(query: String): List<Sticker> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder().url("$baseUrl/stickers/search?q=$encoded").build()
        val arr = executeJsonArray(request)
        return parseStickers(arr)
    }

    private fun parseStickers(arr: JSONArray): List<Sticker> = (0 until arr.length()).map { i ->
        val s = arr.getJSONObject(i)
        Sticker(id = s.optString("_id"), url = s.optString("url"), name = s.optStringOrNull("name"))
    }

    /** Registers this device's FCM token for push notifications. No auth token required
     * (matches the mobile app - see NanChat/src/nano/notifications.ts). */
    fun registerPushToken(fcmToken: String, address: String) {
        val body = JSONObject()
            .put("token", fcmToken)
            .put("addresses", JSONArray().put(JSONObject().put("ticker", "XNO").put("address", address)))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url("$baseUrl/notifications/push/subscribe").post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 304) {
                throw ApiException("Push registration failed (${response.code})")
            }
        }
    }

    /** Creates (or returns) the 1:1 chat with `otherAddress`, returning its chat id. */
    fun createPrivateChat(token: String, myAddress: String, otherAddress: String): String {
        val body = JSONObject()
            .put("type", "private")
            .put("participants", JSONArray(listOf(myAddress, otherAddress)))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url("$baseUrl/chat").header("token", token).post(body).build()
        val json = executeJsonObject(request)
        if (json.has("error")) throw ApiException(json.getString("error"))
        return json.getString("id")
    }

    /** Looks up a single account's display name/avatar, matching the phone app's `/account` fallback. */
    fun fetchAccountProfile(address: String): Pair<String?, String?> {
        val request = Request.Builder().url("$baseUrl/account?account=$address").build()
        val json = executeJsonObject(request)
        return json.optStringOrNull("name") to avatarUrl(json.optJSONObject("profilePicture")?.optStringOrNull("url"))
    }

    /**
     * Resolves a `username` to the account that owns it, or null if no account has that exact
     * username. There's no exact-match/404 endpoint for this server-side (see `/accounts` in
     * server_chat.js) - it's a case-insensitive *substring* search across `name` and `username`, so
     * this fetches candidates and picks the one whose `username` field matches exactly (usernames are
     * always stored lowercased server-side, hence the lowercase compare here).
     */
    fun findAccountByUsername(username: String): AccountLookupResult? {
        val encoded = java.net.URLEncoder.encode(username, "UTF-8")
        val request = Request.Builder().url("$baseUrl/accounts?search=$encoded&limit=25").build()
        val arr = executeJsonArray(request)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optStringOrNull("username")?.equals(username, ignoreCase = true) == true) {
                val address = obj.optStringOrNull("account") ?: obj.optStringOrNull("_id") ?: continue
                return AccountLookupResult(
                    address = address,
                    name = obj.optStringOrNull("name"),
                    avatarUrl = avatarUrl(obj.optJSONObject("profilePicture")?.optStringOrNull("url")),
                )
            }
        }
        return null
    }

    private fun parsePrivateChat(obj: JSONObject, myAddress: String): Chat? {
        val type = obj.optString("type", "private")
        if (type == "group") return null
        val participants = obj.optJSONArray("participants") ?: JSONArray()
        var other: String? = null
        var otherParticipant: JSONObject? = null
        for (i in 0 until participants.length()) {
            val p = participants.getJSONObject(i)
            val id = p.optString("_id")
            if (id.isNotEmpty() && id != myAddress) {
                other = id
                otherParticipant = p
            }
        }
        if (other == null && participants.length() > 0) {
            otherParticipant = participants.getJSONObject(0)
            other = otherParticipant.optString("_id")
        }
        val chatId = obj.optString("id").ifEmpty { return null }
        if (other == null) return null
        return Chat(
            id = chatId,
            type = type,
            otherAccount = other,
            lastMessageEncrypted = obj.optStringOrNull("lastMessage"),
            lastMessageFrom = obj.optStringOrNull("lastMessageFrom"),
            lastMessageTimestamp = obj.optStringOrNull("lastMessageTimestamp"),
            unreadCount = obj.optInt("unreadCount", 0),
            height = obj.optLong("height", -1),
            otherName = otherParticipant?.optStringOrNull("name"),
            otherAvatarUrl = avatarUrl(otherParticipant?.optJSONObject("profilePicture")?.optStringOrNull("url")),
        )
    }

    /** Mirrors the phone app's image proxy (ProfilePicture.tsx) so avatar URLs are resized/cached. */
    private fun avatarUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrEmpty()) return null
        val encoded = java.net.URLEncoder.encode(rawUrl, "UTF-8").replace("+", "%20")
        return "https://i.nanwallet.com/unsafe/rs:fill:80:80/dpr:2/plain/$encoded"
    }

    private fun executeJsonObject(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            return if (bodyStr.isBlank()) JSONObject() else JSONObject(bodyStr)
        }
    }

    private fun executeJsonArray(request: Request): JSONArray {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (bodyStr.isBlank()) return JSONArray()
            return try {
                JSONArray(bodyStr)
            } catch (e: Exception) {
                throw ApiException(JSONObject(bodyStr).optString("error", "Request failed"))
            }
        }
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null
