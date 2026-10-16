package com.piyushpatnaik.stashkeeper.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.piyushpatnaik.stashkeeper.MainActivity
import com.piyushpatnaik.stashkeeper.R
import com.piyushpatnaik.stashkeeper.logic.groceryLines
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ExpiryScheduler {
    private const val workName = "stashkeeper.morning"
    private const val expiryChannel = "stashkeeper.expiry"
    private const val buyChannel = "stashkeeper.buy"

    fun sync(context: Context) {
        val delay = delayUntilNine()
        val request = PeriodicWorkRequestBuilder<MorningWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun show(context: Context) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val store = StashSession.get(context)
        val attention = store.attention().filter { !store.isHandled(it) }
        val notifications = NotificationManagerCompat.from(context)
        if (attention.isEmpty()) {
            notifications.cancel(expiryId)
        } else {
            val title = if (attention.size == 1) attention.first().name else "${attention.size} items need a look"
            val text = attention.take(4).joinToString(", ") { it.name }
            notifications.notify(expiryId, digest(context, expiryChannel, title, text, "shopping").build())
        }
        if (store.buyListNotification) {
            val lines = groceryLines(store.items, store.needs)
            if (lines.isEmpty()) {
                notifications.cancel(buyId)
            } else {
                val text = lines.take(6).joinToString("\n") { "Buy ${it.quantity} ${it.name}" }
                notifications.notify(buyId, digest(context, buyChannel, "To buy", text, "shopping").build())
            }
        } else {
            notifications.cancel(buyId)
        }
    }

    private fun digest(context: Context, channel: String, title: String, text: String, route: String): NotificationCompat.Builder {
        val open = Intent(context, MainActivity::class.java).putExtra("route", route)
        val pending = PendingIntent.getActivity(
            context,
            route.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_stash)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(expiryChannel, "Expiry reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A morning note when something in the stash should be used or replaced."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(buyChannel, "Buy list", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The groceries StashKeeper thinks you should pick up."
            },
        )
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun delayUntilNine(): Duration {
        val now = ZonedDateTime.now()
        var next = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private const val expiryId = 41
    private const val buyId = 42
}

class MorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        withContext(Dispatchers.Main) { ExpiryScheduler.show(applicationContext) }
        return Result.success()
    }
}
