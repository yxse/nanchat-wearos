package com.nanchat.app.notifications

/** Tracks whether the app's UI is currently visible, so the live-socket notification path
 * (ChatRepository, while the process is alive) and the FCM push path (NanChatMessagingService,
 * which can wake up a fresh process) don't both fire a notification for the same message. */
object AppForeground {
    @Volatile
    var inForeground: Boolean = false
}
