package com.piyushpatnaik.stashkeeper.data

import android.content.Context
import android.os.Looper

/**
 * One in-memory stash for the screen, the morning notification, and an
 * assistant call. A second store would show a different household than the
 * one just saved.
 */
object StashSession {
    @Volatile
    private var current: StashStore? = null

    fun get(context: Context): StashStore {
        current?.let { return it }
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "The stash is opened on the main thread."
        }
        synchronized(this) {
            current?.let { return it }
            return StashStore(context.applicationContext).also { current = it }
        }
    }
}
