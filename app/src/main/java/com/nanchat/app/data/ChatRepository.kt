package com.nanchat.app.data

import android.content.Context
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import com.nanchat.app.crypto.Bip39
import com.nanchat.app.crypto.NanoCrypto
import com.nanchat.app.network.ApiException
import com.nanchat.app.network.ChatApi
import com.nanchat.app.network.ChatSocket
import com.nanchat.app.notifications.AppForeground
import com.nanchat.app.notifications.NotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

sealed class Session {
    data object LoggedOut : Session()
    data class LoggedIn(val address: String, val privateKeyHex: String) : Session()
}

/**
 * Owns the chat state for the whole app: login (seed -> Nano account), the private chat list
 * (groups are filtered out entirely), the currently open conversation and realtime send/receive.
 */
class ChatRepository(private val secureStore: SecureStore, private val appContext: Context) {

    companion object {
        private const val TAG = "NanChatRepo"
        private const val MESSAGES_PAGE_SIZE = 30
        /** Wear OS devices routinely hit a transient "unable to resolve host" right at process/network
         * cold start (the watch is still switching between the phone's Bluetooth companion proxy and
         * WiFi and DNS isn't ready yet) - retry a few times with backoff instead of giving up after a
         * single attempt, both for the socket connection and for FCM push token registration. */
        private const val MAX_CONNECT_RETRIES = 4
        private const val MAX_PUSH_TOKEN_RETRIES = 4
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val api = ChatApi()
    private val socket = ChatSocket()

    private enum class SocketStatus { DISCONNECTED, CONNECTING, CONNECTED }
    private var socketStatus = SocketStatus.DISCONNECTED
    private var connectJob: Job? = null

    init {
        NotificationHelper.ensureChannel(appContext)
    }

    private val _session = MutableStateFlow<Session>(Session.LoggedOut)
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private var openChatId: String? = null
    private var openChat: Chat? = null
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Separate from [loading] so the conversation screen can show a loading state (M3 Expressive
     * `LoadingIndicator`) specifically for "messages fetched but not decrypted yet", without it
     * flickering every time the chat list also happens to be refreshing in the background. */
    private val _messagesLoading = MutableStateFlow(false)
    val messagesLoading: StateFlow<Boolean> = _messagesLoading.asStateFlow()

    /** True while fetching an older page of messages for the currently open chat (see
     * [loadOlderMessages]) - shown as a small indicator at the top of the conversation. */
    private val _loadingOlderMessages = MutableStateFlow(false)
    val loadingOlderMessages: StateFlow<Boolean> = _loadingOlderMessages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _hiddenGroupCount = MutableStateFlow(0)
    val hiddenGroupCount: StateFlow<Int> = _hiddenGroupCount.asStateFlow()

    private val profileCache = mutableMapOf<String, Pair<String?, String?>>()

    /** Decrypted messages already seen this session, per chat, keyed by message id - reopening a
     * conversation shows these instantly and only decrypts whatever's new in the latest fetch,
     * instead of re-running native NaCl-box decryption on the whole page of messages every time. */
    private val decryptedMessageCache = mutableMapOf<String, MutableMap<String, Message>>()

    /** False once a chat's oldest page came back short of a full page (server has nothing older). */
    private val chatHasMoreOlder = mutableMapOf<String, Boolean>()
    private val chatLoadingOlder = mutableSetOf<String>()

    init {
        secureStore.seed?.let { restoreSession(it) }
    }

    private fun restoreSession(seed: String) {
        val account = NanoCrypto.deriveAccount(seed)
        _session.value = Session.LoggedIn(account.address, account.privateKeyHex)
        connectAndLoad(account.address, account.privateKeyHex)
        registerPushToken(account.address)
    }

    /** Registers this device's FCM token with the backend (`POST /notifications/push/subscribe`,
     * no auth required - see server_notifications.js) so messages can be delivered as push
     * notifications when the app process isn't alive to receive them over the socket. */
    private fun registerPushToken(address: String, attempt: Int = 0) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = task.result
            if (token == null) {
                Log.e(TAG, "registerPushToken: no FCM token (attempt $attempt)", task.exception)
                retryRegisterPushToken(address, attempt)
                return@addOnCompleteListener
            }
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { api.registerPushToken(token, address) }
                    Log.d(TAG, "registerPushToken: registered (attempt $attempt)")
                } catch (e: Exception) {
                    Log.e(TAG, "registerPushToken failed (attempt $attempt): ${e.message}", e)
                    retryRegisterPushToken(address, attempt)
                }
            }
        }
    }

    private fun retryRegisterPushToken(address: String, attempt: Int) {
        if (attempt >= MAX_PUSH_TOKEN_RETRIES) return
        scope.launch {
            delay((attempt + 1) * 2000L)
            registerPushToken(address, attempt + 1)
        }
    }

    private fun loginWithSeed(seed: String): Boolean {
        if (!NanoCrypto.isValidSeed(seed)) {
            _error.value = "Invalid seed (expected 64 hex characters)"
            return false
        }
        secureStore.seed = seed
        secureStore.chatToken = null
        restoreSession(seed)
        return true
    }

    fun loginWithMnemonic(words: List<String>): Boolean {
        val seed = try {
            Bip39.mnemonicToSeed(words)
        } catch (e: IllegalArgumentException) {
            _error.value = e.message ?: "Invalid phrase"
            return false
        }
        return loginWithSeed(seed)
    }

    fun clearError() {
        _error.value = null
    }

    private fun connectAndLoad(address: String, privateKeyHex: String) {
        // Guards against redundant reconnects: e.g. MainActivity.onStart() calling onAppForeground()
        // moments after this repository's own init already started connecting at cold start, or the
        // user backgrounding/foregrounding the app faster than a connection attempt resolves.
        if (socketStatus != SocketStatus.DISCONNECTED) return
        socketStatus = SocketStatus.CONNECTING
        connectJob = scope.launch {
            _loading.value = true
            var attempt = 0
            while (true) {
                try {
                    val token = getValidToken(address, privateKeyHex)
                    socket.connect(
                        token = token,
                        myAddress = address,
                        onMessage = { message -> handleIncomingMessage(message, address, privateKeyHex) },
                        onConnected = { socketStatus = SocketStatus.CONNECTED },
                    )
                    loadChats(address, privateKeyHex)
                    // The socket was disconnected while backgrounded (onAppBackground), so any messages
                    // sent to the currently open conversation in the meantime were never received - pull
                    // the latest page now that we're back on screen, same as first opening it.
                    openChat?.let { openChat(it) }
                    // Also re-attempt push token registration here, not just at login: if it failed at
                    // cold start (e.g. the watch's network wasn't fully up yet - a common Wear OS
                    // startup race) it would otherwise never retry again once its own short retry
                    // budget is exhausted. This point proves the network is actually up.
                    registerPushToken(address)
                    break
                } catch (e: Exception) {
                    attempt++
                    Log.e(TAG, "connectAndLoad failed (attempt $attempt): ${e.message}", e)
                    // Bail out if the app went to background mid-retry (onAppBackground already reset
                    // socketStatus to DISCONNECTED) rather than fighting that state on a stale attempt.
                    if (attempt >= MAX_CONNECT_RETRIES || socketStatus != SocketStatus.CONNECTING) {
                        _error.value = e.message ?: "Connection failed"
                        socketStatus = SocketStatus.DISCONNECTED
                        break
                    }
                    delay(attempt * 1500L)
                }
            }
            _loading.value = false
        }
    }

    /**
     * The socket (and everything it triggers - incoming-message handling, chat refreshes) is only
     * meant to be alive while the app is actually on screen; while backgrounded, push notifications
     * (see NanChatMessagingService) cover delivery instead, so there's no need to hold a live
     * connection or make any network calls at all. Call from the activity's onStart/onStop.
     */
    fun onAppForeground() {
        val loggedIn = _session.value as? Session.LoggedIn ?: return
        connectAndLoad(loggedIn.address, loggedIn.privateKeyHex)
    }

    fun onAppBackground() {
        // Also cancel any in-flight connectAndLoad retry loop (e.g. stuck in its backoff delay) -
        // otherwise a fast background/foreground cycle (screen wake, config change) could leave that
        // stale coroutine racing a fresh one from the next onAppForeground, both mutating socketStatus.
        connectJob?.cancel()
        connectJob = null
        socket.disconnect()
        socketStatus = SocketStatus.DISCONNECTED
    }

    fun refreshChats() {
        val loggedIn = _session.value as? Session.LoggedIn ?: return
        loadChats(loggedIn.address, loggedIn.privateKeyHex)
    }

    private fun loadChats(address: String, privateKeyHex: String) {
        scope.launch {
            try {
                val token = getValidToken(address, privateKeyHex)
                val result = withContext(Dispatchers.IO) { api.fetchChats(token, address) }
                Log.d(
                    TAG,
                    "loadChats: fetched ${result.privateChats.size} private chat(s), " +
                        "${result.hiddenGroupCount} group chat(s) hidden, for $address",
                )
                _hiddenGroupCount.value = result.hiddenGroupCount
                _chats.value = withContext(Dispatchers.Default) {
                    result.privateChats
                        .map { it.copy(lastMessagePreview = decryptPreview(it, privateKeyHex)) }
                        .sortedByDescending { it.lastMessageTimestamp.orEmpty() }
                }
                enrichMissingProfiles()
            } catch (e: Exception) {
                Log.e(TAG, "loadChats failed", e)
                _error.value = e.message ?: "Could not load chats"
            }
        }
    }

    /**
     * `/chats` embeds the other participant's name/avatar when the server already knows them, but not
     * always (e.g. brand new chats). For those, fall back to the same per-account lookup the phone app
     * uses (`/account?account=`), same as ProfileNameLookup does client-side there.
     */
    private fun enrichMissingProfiles() {
        val toResolve = _chats.value.filter { it.otherName.isNullOrEmpty() && !profileCache.containsKey(it.otherAccount) }
        if (toResolve.isEmpty()) return
        scope.launch {
            for (chat in toResolve) {
                val profile = try {
                    withContext(Dispatchers.IO) { api.fetchAccountProfile(chat.otherAccount) }
                } catch (e: Exception) {
                    null to null
                }
                profileCache[chat.otherAccount] = profile
                val (name, avatarUrl) = profile
                if (name != null || avatarUrl != null) {
                    _chats.value = _chats.value.map {
                        if (it.otherAccount == chat.otherAccount) it.copy(otherName = name, otherAvatarUrl = avatarUrl) else it
                    }
                }
            }
        }
    }

    private suspend fun getValidToken(address: String, privateKeyHex: String): String =
        withContext(Dispatchers.IO) {
            val cachedToken = secureStore.chatToken
            if (cachedToken != null && secureStore.chatTokenExpiresAt > System.currentTimeMillis()) {
                return@withContext cachedToken
            }
            val message = "Login to nanwallet.com chat. Date:${Instant.now()}"
            val signature = NanoCrypto.signMessage(privateKeyHex, "Signed Message: $message")
            Log.d(TAG, "getValidToken: requesting new token for $address")
            val (token, expiresAt) = api.fetchToken(address, message, signature)
            secureStore.chatToken = token
            secureStore.chatTokenExpiresAt = expiresAt
            token
        }

    fun openChat(chat: Chat) {
        val loggedIn = _session.value as? Session.LoggedIn ?: return
        openChatId = chat.id
        openChat = chat
        val chatCache = decryptedMessageCache.getOrPut(chat.id) { mutableMapOf() }
        // Show whatever we've already decrypted this session immediately - reopening a conversation
        // you've already viewed shouldn't flash the loading indicator or redo native decryption.
        _messages.value = chatCache.values.sortedBy { it.height }
        markChatRead(chat.id)
        scope.launch {
            _messagesLoading.value = chatCache.isEmpty()
            try {
                val token = getValidToken(loggedIn.address, loggedIn.privateKeyHex)
                val fetched = withContext(Dispatchers.IO) {
                    api.fetchMessages(token, chat.id, cursor = -1, limit = MESSAGES_PAGE_SIZE)
                }
                chatHasMoreOlder[chat.id] = fetched.size >= MESSAGES_PAGE_SIZE
                val toDecrypt = fetched.filter { it.id !in chatCache }
                if (toDecrypt.isNotEmpty()) {
                    // NaCl-box decryption is CPU-bound native (libsodium) work - run it off the main
                    // thread, and only for messages this cache doesn't already have decrypted.
                    val newlyDecrypted = withContext(Dispatchers.Default) {
                        toDecrypt.mapNotNull { decryptMessage(it, loggedIn) }
                    }
                    newlyDecrypted.forEach { chatCache[it.id] = it }
                }
                if (openChatId == chat.id) {
                    _messages.value = chatCache.values.sortedBy { it.height }
                }
            } catch (e: Exception) {
                if (chatCache.isEmpty()) _error.value = e.message ?: "Could not load messages"
            } finally {
                _messagesLoading.value = false
            }
        }
    }

    /**
     * Fetches the next page of older messages (lower height) for the currently open chat, scrolled
     * into view by [ChatScreen] as the user reaches the top of the conversation. No-ops if a page is
     * already in flight, nothing older is known to remain, or nothing's loaded yet to page back from
     * (that first page is [openChat]'s job).
     */
    fun loadOlderMessages(chat: Chat) {
        if (chat.id != openChatId) return
        if (chat.id in chatLoadingOlder) return
        if (chatHasMoreOlder[chat.id] == false) return
        val loggedIn = _session.value as? Session.LoggedIn ?: return
        val chatCache = decryptedMessageCache[chat.id]
        val oldestHeight = chatCache?.values?.minOfOrNull { it.height } ?: return
        if (oldestHeight <= 0) {
            chatHasMoreOlder[chat.id] = false
            return
        }
        chatLoadingOlder += chat.id
        _loadingOlderMessages.value = true
        scope.launch {
            try {
                val token = getValidToken(loggedIn.address, loggedIn.privateKeyHex)
                val fetched = withContext(Dispatchers.IO) {
                    api.fetchMessages(token, chat.id, cursor = oldestHeight - 1, limit = MESSAGES_PAGE_SIZE)
                }
                chatHasMoreOlder[chat.id] = fetched.size >= MESSAGES_PAGE_SIZE
                val toDecrypt = fetched.filter { it.id !in chatCache }
                if (toDecrypt.isNotEmpty()) {
                    val newlyDecrypted = withContext(Dispatchers.Default) {
                        toDecrypt.mapNotNull { decryptMessage(it, loggedIn) }
                    }
                    newlyDecrypted.forEach { chatCache[it.id] = it }
                }
                if (openChatId == chat.id) {
                    _messages.value = chatCache.values.sortedBy { it.height }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadOlderMessages failed", e)
            } finally {
                chatLoadingOlder -= chat.id
                _loadingOlderMessages.value = false
            }
        }
    }

    fun closeChat() {
        openChatId = null
        openChat = null
        _messages.value = emptyList()
        _messagesLoading.value = false
    }

    /** Returns null (message dropped) rather than a placeholder when content can't be decrypted -
     * e.g. unsupported message types that aren't plain NaCl-box encrypted text. Sticker messages are
     * sent unencrypted (see [ChatSocket.sendSticker]), so they skip decryption entirely. */
    private fun decryptMessage(message: Message, loggedIn: Session.LoggedIn): Message? {
        if (message.stickerUrl != null) {
            message.decrypted = message.contentEncrypted
            return message
        }
        val other = if (message.fromAccount == loggedIn.address) message.toAccount else message.fromAccount
        val decrypted = try {
            NanoCrypto.boxDecrypt(message.contentEncrypted, other, loggedIn.privateKeyHex)
        } catch (e: Exception) {
            return null
        }
        message.decrypted = decrypted
        return message
    }

    private fun decryptPreview(chat: Chat, privateKeyHex: String): String? {
        val encrypted = chat.lastMessageEncrypted ?: return null
        return try {
            NanoCrypto.boxDecrypt(encrypted, chat.otherAccount, privateKeyHex)
        } catch (e: Exception) {
            null
        }
    }

    private fun handleIncomingMessage(message: Message, myAddress: String, privateKeyHex: String) {
        val decrypted = decryptMessage(message, Session.LoggedIn(myAddress, privateKeyHex)) ?: return
        decryptedMessageCache.getOrPut(message.chatId) { mutableMapOf() }[decrypted.id] = decrypted
        if (message.chatId == openChatId) {
            val current = _messages.value
            // The server echoes our own sends back over the socket to confirm delivery. Replace the
            // optimistic local bubble (matched by its still-encrypted content) instead of appending a
            // second one for it.
            val pendingIndex = current.indexOfFirst { it.pending && it.contentEncrypted == decrypted.contentEncrypted }
            _messages.value = when {
                current.any { it.id == decrypted.id } -> current
                pendingIndex >= 0 -> current.toMutableList().apply { set(pendingIndex, decrypted) }
                else -> current + decrypted
            }
        }
        // Only notify for messages from someone else, arriving to a chat that isn't the one currently
        // open on screen (avoids notifying about our own sends echoed back, or the open conversation).
        if (decrypted.fromAccount != myAddress && (message.chatId != openChatId || !AppForeground.inForeground)) {
            notifyIncomingMessage(decrypted)
        }
        loadChats(myAddress, privateKeyHex)
    }

    /** Announces that a message arrived and from whom - never its content, even though it's already
     * decrypted in memory at this point, so a locked watch screen never leaks a conversation. */
    private fun notifyIncomingMessage(message: Message) {
        val chat = _chats.value.find { it.id == message.chatId }
        val title = chat?.otherName?.takeIf { it.isNotBlank() } ?: message.fromAccount
        NotificationHelper.notifyMessage(appContext, message.chatId, title, "New message")
    }

    fun sendMessage(chat: Chat, text: String) {
        val loggedIn = _session.value as? Session.LoggedIn ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val encrypted = try {
            NanoCrypto.boxEncrypt(trimmed, chat.otherAccount, loggedIn.privateKeyHex)
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage: encryption failed", e)
            _error.value = "Encryption failed"
            return
        }
        val localMessage = Message(
            id = "local-${System.nanoTime()}",
            chatId = chat.id,
            contentEncrypted = encrypted,
            fromAccount = loggedIn.address,
            toAccount = chat.otherAccount,
            timestamp = Instant.now().toString(),
            height = (_messages.value.maxOfOrNull { it.height } ?: 0) + 1,
            decrypted = trimmed,
            pending = true,
        )
        _messages.value = _messages.value + localMessage
        socket.sendMessage(
            chatId = chat.id,
            encryptedContent = encrypted,
            toAccount = chat.otherAccount,
            fromAccount = loggedIn.address,
        ) { success, error ->
            if (!success) {
                _error.value = error ?: "Failed to send message"
            }
        }
    }

    fun sendSticker(chat: Chat, stickerUrl: String) {
        val loggedIn = _session.value as? Session.LoggedIn ?: return
        val localMessage = Message(
            id = "local-${System.nanoTime()}",
            chatId = chat.id,
            contentEncrypted = "Sticker",
            fromAccount = loggedIn.address,
            toAccount = chat.otherAccount,
            timestamp = Instant.now().toString(),
            height = (_messages.value.maxOfOrNull { it.height } ?: 0) + 1,
            decrypted = "Sticker",
            pending = true,
            stickerUrl = stickerUrl,
        )
        _messages.value = _messages.value + localMessage
        socket.sendSticker(
            chatId = chat.id,
            stickerUrl = stickerUrl,
            toAccount = chat.otherAccount,
            fromAccount = loggedIn.address,
        ) { success, error ->
            if (!success) {
                _error.value = error ?: "Failed to send sticker"
            }
        }
    }

    private val _stickerCollections = MutableStateFlow<List<StickerCollection>>(emptyList())
    val stickerCollections: StateFlow<List<StickerCollection>> = _stickerCollections.asStateFlow()

    private val stickersByCollection = mutableMapOf<String, List<Sticker>>()

    fun loadStickerCollections() {
        val loggedIn = _session.value as? Session.LoggedIn ?: return
        if (_stickerCollections.value.isNotEmpty()) return
        scope.launch {
            try {
                val token = getValidToken(loggedIn.address, loggedIn.privateKeyHex)
                val collections = withContext(Dispatchers.IO) { api.fetchStickerCollections(token) }
                _stickerCollections.value = collections
                // Warm the sticker cache eagerly (metadata + images) so the picker opens instantly
                // and fully offline, instead of loading each pack on first visit.
                collections.forEach { col -> loadStickers(col.id) {} }
            } catch (e: Exception) {
                Log.e(TAG, "loadStickerCollections failed", e)
            }
        }
    }

    fun loadStickers(collectionId: String, onResult: (List<Sticker>) -> Unit) {
        stickersByCollection[collectionId]?.let { onResult(it); return }
        scope.launch {
            try {
                val stickers = withContext(Dispatchers.IO) { api.fetchStickers(collectionId) }
                stickersByCollection[collectionId] = stickers
                onResult(stickers)
                prefetchStickerImages(stickers)
            } catch (e: Exception) {
                Log.e(TAG, "loadStickers failed", e)
                onResult(emptyList())
            }
        }
    }

    private fun prefetchStickerImages(stickers: List<Sticker>) {
        val imageLoader = appContext.imageLoader
        stickers.forEach { sticker ->
            imageLoader.enqueue(ImageRequest.Builder(appContext).data(sticker.url).build())
        }
    }

    fun searchStickers(query: String, onResult: (List<Sticker>) -> Unit) {
        scope.launch {
            try {
                onResult(withContext(Dispatchers.IO) { api.searchStickers(query) })
            } catch (e: Exception) {
                Log.e(TAG, "searchStickers failed", e)
                onResult(emptyList())
            }
        }
    }

    /**
     * Starts (or returns the existing) 1:1 chat with the account that owns `username`. Unlike the old
     * raw-address flow, this always resolves and confirms the account exists first - there's no
     * exact-match lookup server-side (see [com.nanchat.app.network.ChatApi.findAccountByUsername]),
     * so a typo just looks like "user not found" rather than silently creating a chat with garbage.
     */
    fun startChatWithUsername(username: String, onResult: (Chat?) -> Unit) {
        val loggedIn = _session.value as? Session.LoggedIn ?: return
        val normalized = username.trim().lowercase()
        if (!normalized.matches(Regex("^[a-z0-9_]{6,24}$"))) {
            _error.value = "Enter a valid NanChat ID (6-24 letters, numbers, underscore)"
            onResult(null)
            return
        }
        scope.launch {
            _loading.value = true
            try {
                val account = withContext(Dispatchers.IO) { api.findAccountByUsername(normalized) }
                    ?: throw ApiException("No user found with that NanChat ID")
                val existing = _chats.value.find { it.otherAccount == account.address }
                if (existing != null) {
                    onResult(existing)
                    return@launch
                }
                val token = getValidToken(loggedIn.address, loggedIn.privateKeyHex)
                val chatId = withContext(Dispatchers.IO) {
                    api.createPrivateChat(token, loggedIn.address, account.address)
                }
                val chat = Chat(
                    id = chatId,
                    type = "private",
                    otherAccount = account.address,
                    lastMessageEncrypted = null,
                    lastMessageFrom = null,
                    lastMessageTimestamp = null,
                    unreadCount = 0,
                    height = -1,
                    otherName = account.name,
                    otherAvatarUrl = account.avatarUrl,
                )
                _chats.value = listOf(chat) + _chats.value
                onResult(chat)
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not start chat"
                onResult(null)
            } finally {
                _loading.value = false
            }
        }
    }

    private fun markChatRead(chatId: String) {
        _chats.value = _chats.value.map { if (it.id == chatId) it.copy(unreadCount = 0) else it }
    }
}
