package com.nanchat.app.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import com.nanchat.app.NanChatApplication
import com.nanchat.app.data.Chat
import com.nanchat.app.data.ChatRepository
import com.nanchat.app.data.Session
import com.nanchat.app.notifications.AppForeground
import com.nanchat.app.presentation.theme.NanChatTheme
import com.nanchat.app.ui.ChatListScreen
import com.nanchat.app.ui.ChatScreen
import com.nanchat.app.ui.NewChatScreen
import com.nanchat.app.ui.OnboardingScreen
import com.nanchat.app.ui.SeedScreen

private sealed interface NavScreen {
    data object ChatList : NavScreen
    data object NewChat : NavScreen
    data object ShowSeed : NavScreen
    data class ChatDetail(val chat: Chat) : NavScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as NanChatApplication).repository
        setContent {
            NanChatTheme {
                NanChatApp(repository)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppForeground.inForeground = true
        // No network connection (socket or otherwise) is kept while the app isn't on screen - push
        // notifications (NanChatMessagingService) cover message delivery in that case instead.
        (application as NanChatApplication).repository.onAppForeground()
    }

    override fun onStop() {
        AppForeground.inForeground = false
        (application as NanChatApplication).repository.onAppBackground()
        super.onStop()
    }
}

@Composable
private fun NanChatApp(repository: ChatRepository) {
    val session by repository.session.collectAsState()
    val chats by repository.chats.collectAsState()
    val messages by repository.messages.collectAsState()
    val loading by repository.loading.collectAsState()
    val messagesLoading by repository.messagesLoading.collectAsState()
    val loadingOlderMessages by repository.loadingOlderMessages.collectAsState()
    val error by repository.error.collectAsState()
    val hiddenGroupCount by repository.hiddenGroupCount.collectAsState()
    val stickerCollections by repository.stickerCollections.collectAsState()

    var nav by remember { mutableStateOf<NavScreen>(NavScreen.ChatList) }
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    // Requested once the user is actually logged in - not unconditionally at cold start - since
    // notifications only become relevant once there's an account that can receive messages (Wear
    // guide: request permissions only when it's contextually clear why they're needed).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val isLoggedIn = session is Session.LoggedIn
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when (val currentSession = session) {
        is Session.LoggedOut -> {
            OnboardingScreen(
                error = error,
                onImportMnemonic = { words -> repository.loginWithMnemonic(words) },
                onCreateAccount = { seed, name -> repository.createAccount(seed, name) },
                onDismissError = { repository.clearError() },
            )
        }
        is Session.LoggedIn -> {
            when (val screen = nav) {
                is NavScreen.ChatList -> {
                    ChatListScreen(
                        chats = chats,
                        loading = loading,
                        error = error,
                        hiddenGroupCount = hiddenGroupCount,
                        appVersion = appVersion,
                        onOpenChat = { chat ->
                            repository.openChat(chat)
                            nav = NavScreen.ChatDetail(chat)
                        },
                        onNewChat = { nav = NavScreen.NewChat },
                        onRetry = { repository.refreshChats() },
                        onShowSeed = { nav = NavScreen.ShowSeed },
                        onLogout = {
                            nav = NavScreen.ChatList
                            repository.logout()
                        },
                    )
                }
                is NavScreen.ShowSeed -> {
                    BackHandler { nav = NavScreen.ChatList }
                    SwipeToDismissBox(onDismissed = { nav = NavScreen.ChatList }) { isBackground ->
                        if (isBackground) {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        } else {
                            SeedScreen(mnemonic = repository.currentMnemonic().orEmpty())
                        }
                    }
                }
                is NavScreen.NewChat -> {
                    // SwipeToDismissBox gives a generous swipe area (not just the outer pixel
                    // edge); BackHandler keeps the system back gesture/button working too, since
                    // SwipeToDismissBox alone doesn't intercept it (it would otherwise exit the app).
                    BackHandler { nav = NavScreen.ChatList }
                    SwipeToDismissBox(onDismissed = { nav = NavScreen.ChatList }) { isBackground ->
                        if (isBackground) {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        } else {
                            NewChatScreen(
                                loading = loading,
                                error = error,
                                onDismissError = { repository.clearError() },
                                onStartChat = { username ->
                                    repository.startChatWithUsername(username) { chat ->
                                        if (chat != null) {
                                            repository.openChat(chat)
                                            nav = NavScreen.ChatDetail(chat)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                is NavScreen.ChatDetail -> {
                    val goBack = {
                        repository.closeChat()
                        nav = NavScreen.ChatList
                    }
                    BackHandler(onBack = goBack)
                    SwipeToDismissBox(onDismissed = goBack) { isBackground ->
                        if (isBackground) {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        } else {
                            ChatScreen(
                                messages = messages,
                                loading = messagesLoading,
                                loadingOlder = loadingOlderMessages,
                                myAddress = currentSession.address,
                                onSend = { text -> repository.sendMessage(screen.chat, text) },
                                onLoadOlderMessages = { repository.loadOlderMessages(screen.chat) },
                                stickerCollections = stickerCollections,
                                onOpenStickerPicker = { repository.loadStickerCollections() },
                                onLoadStickers = { collectionId, onResult -> repository.loadStickers(collectionId, onResult) },
                                onSendSticker = { url -> repository.sendSticker(screen.chat, url) },
                            )
                        }
                    }
                }
            }
        }
    }
}
