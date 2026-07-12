package com.nanchat.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.nanchat.app.crypto.Bip39
import com.nanchat.app.data.ChatRepository
import com.nanchat.app.data.SecureStore

class NanChatApplication : Application(), ImageLoaderFactory {
    val repository: ChatRepository by lazy { ChatRepository(SecureStore(this), this) }

    override fun onCreate() {
        super.onCreate()
        Bip39.init(this)
    }

    // Stickers (and avatars) should stay fully cached once fetched, so reopening the picker never
    // re-downloads them - a generous, persistent disk cache independent of server cache headers.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()
}
