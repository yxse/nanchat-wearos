package com.nanchat.app.data

data class Chat(
    val id: String,
    val type: String,
    val otherAccount: String,
    val lastMessageEncrypted: String?,
    val lastMessageFrom: String?,
    val lastMessageTimestamp: String?,
    val unreadCount: Int,
    val height: Long,
    val lastMessagePreview: String? = null,
    val otherName: String? = null,
    val otherAvatarUrl: String? = null,
) {
    val isPrivate: Boolean get() = type != "group"
}

data class Message(
    val id: String,
    val chatId: String,
    val contentEncrypted: String,
    val fromAccount: String,
    val toAccount: String,
    val timestamp: String,
    val height: Long,
    var decrypted: String? = null,
    var pending: Boolean = false,
    /** Set for sticker messages (holds the sticker's image URL). Sticker messages are sent with
     * plain, unencrypted content ("Sticker") - see [com.nanchat.app.data.ChatRepository]. */
    val stickerUrl: String? = null,
)

data class StickerCollection(val id: String, val name: String, val logo: String)

data class Sticker(val id: String, val url: String, val name: String?)
