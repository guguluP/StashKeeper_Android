package com.piyushpatnaik.stashkeeper.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import com.piyushpatnaik.stashkeeper.MainActivity
import com.piyushpatnaik.stashkeeper.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android's Live Update: the same job as an iOS Live Activity.
 * On Android 16 and newer the ongoing progress notification is promoted
 * onto the status bar and the lock screen. Older releases keep the same
 * ongoing notification without that chip.
 */
object ScanLiveUpdate {
    private const val channelId = "stashkeeper.live"
    private const val notificationId = 77
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var finishJob: kotlinx.coroutines.Job? = null

    fun start(context: Context, message: String) {
        finishJob?.cancel()
        post(context, message, fraction = 0.02f, itemsFound = 0, complete = false)
    }

    fun update(context: Context, message: String, fraction: Float, itemsFound: Int) {
        post(context, message, fraction.coerceIn(0f, 0.97f), itemsFound, complete = false)
    }

    fun finish(context: Context, itemsFound: Int) {
        val message = if (itemsFound == 0) "No items found" else "Found $itemsFound item${if (itemsFound == 1) "" else "s"}"
        post(context, message, fraction = 1f, itemsFound = itemsFound, complete = true)
        finishJob?.cancel()
        finishJob = scope.launch {
            delay(2_000)
            end(context)
        }
    }

    fun end(context: Context) {
        finishJob?.cancel()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)
    }

    private fun post(context: Context, message: String, fraction: Float, itemsFound: Int, complete: Boolean) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        val open = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).putExtra("route", "add"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_stash)
            .setContentTitle("StashKeeper")
            .setContentText(message)
            .setSubText(if (itemsFound > 0) "$itemsFound found so far" else null)
            .setOngoing(!complete)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setContentIntent(open)
            .setAutoCancel(complete)
            .setColor(0xFF0C6B52.toInt())
            .setCategory(Notification.CATEGORY_PROGRESS)
        promote(context, builder, percent, if (complete) "Done" else "$percent%")
        context.getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
    }

    private fun promote(context: Context, builder: Notification.Builder, percent: Int, shortText: String) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(builder, true)
            builder.javaClass.getMethod("setShortCriticalText", String::class.java).invoke(builder, shortText)
        }
        runCatching {
            val styleClass = Class.forName("android.app.Notification\$ProgressStyle")
            val segmentClass = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
            val pointClass = Class.forName("android.app.Notification\$ProgressStyle\$Point")
            val style = styleClass.getDeclaredConstructor().newInstance()
            // Lengths sum to 100 and line up with setProgress: read, look up, finish.
            val read = segment(segmentClass, 20, 0xFF31C398.toInt(), 1)
            val match = segment(segmentClass, 60, 0xFF0C6B52.toInt(), 2)
            val finish = segment(segmentClass, 20, 0xFF4CCDA9.toInt(), 3)
            styleClass.getMethod("addProgressSegment", segmentClass).invoke(style, read)
            styleClass.getMethod("addProgressSegment", segmentClass).invoke(style, match)
            styleClass.getMethod("addProgressSegment", segmentClass).invoke(style, finish)
            styleClass.getMethod("addProgressPoint", pointClass).invoke(style, point(pointClass, 20, 0xFF0C6B52.toInt(), 1))
            styleClass.getMethod("addProgressPoint", pointClass).invoke(style, point(pointClass, 80, 0xFF0C6B52.toInt(), 2))
            styleClass.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(style, percent)
            styleClass.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType).invoke(style, true)
            runCatching {
                val icon = Icon.createWithResource(context, R.drawable.ic_stat_stash)
                styleClass.getMethod("setProgressTrackerIcon", Icon::class.java).invoke(style, icon)
            }
            runCatching {
                // Semantic colors arrived with Android 17. Android 16 keeps the explicit colors.
                val semantic = segmentClass.getMethod("setSemanticStyle", Int::class.javaPrimitiveType)
                semantic.invoke(read, 1)
                semantic.invoke(match, 0)
                semantic.invoke(finish, 2)
            }
            builder.setStyle(style as Notification.Style)
        }
    }

    private fun segment(segmentClass: Class<*>, length: Int, color: Int, id: Int): Any {
        val segment = segmentClass.getConstructor(Int::class.javaPrimitiveType).newInstance(length)
        segmentClass.getMethod("setColor", Int::class.javaPrimitiveType).invoke(segment, color)
        segmentClass.getMethod("setId", Int::class.javaPrimitiveType).invoke(segment, id)
        return segment
    }

    private fun point(pointClass: Class<*>, position: Int, color: Int, id: Int): Any {
        val point = pointClass.getConstructor(Int::class.javaPrimitiveType).newInstance(position)
        pointClass.getMethod("setColor", Int::class.javaPrimitiveType).invoke(point, color)
        pointClass.getMethod("setId", Int::class.javaPrimitiveType).invoke(point, id)
        return point
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Scan in progress", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Shows a receipt or pack scan on the lock screen while StashKeeper is reading it."
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }
}
