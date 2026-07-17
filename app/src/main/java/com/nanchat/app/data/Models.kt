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
    val muted: Boolean = false,
    /** Whether the signed-in account has accepted this chat (`/accept-chat`) - the other
     * participant's chats start unaccepted for everyone but its creator (see `register.js`'s
     * `createChat`). Defaults true so locally-built chats (e.g. ones we started ourselves) never
     * need special-casing. */
    val accepted: Boolean = true,
    /** The account that started this chat - null only if the server omitted it, in which case
     * [isPendingRequest] treats the chat as already accepted rather than risk wrongly gating an
     * existing chat. */
    val creator: String? = null,
) {
    val isPrivate: Boolean get() = type != "group"

    /** True for a chat someone else started with us that we haven't accepted or blocked yet - the
     * phone app's "message request" state (see NewMessageWarning.tsx's `isNewChat`). */
    fun isPendingRequest(myAddress: String): Boolean = !accepted && creator != null && creator != myAddress
}

/** An account the signed-in user has blocked (`/block-chat`) - listed in Settings so the block can
 * be reversed. Unlike [Chat], this isn't necessarily a chat the user has: blocking someone by
 * address alone (before any chat exists) still shows up here (see `/blocked-chats`). */
data class BlockedAccount(val address: String, val name: String?, val avatarUrl: String?)

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
