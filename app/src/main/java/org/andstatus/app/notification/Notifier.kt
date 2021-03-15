/* 
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.notification

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import org.andstatus.app.R
import org.andstatus.app.appwidget.AppWidgets
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.MyProvider
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.UriUtils
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.stream.Collectors

class Notifier(val myContext: MyContext) {
    private var nM: NotificationManager? = null
    private var notificationArea = false
    private var vibration = false
    private var soundUri: Uri? = null
    private var enabledEvents: MutableList<NotificationEventType> = mutableListOf()
    private val refEvents: AtomicReference<NotificationEvents> = AtomicReference(NotificationEvents.EMPTY)
    fun clearAll() {
        AppWidgets.of(refEvents.updateAndGet { obj: NotificationEvents -> obj.clearAll() }).clearCounters().updateViews()
        clearAndroidNotifications()
    }

    fun clear(timeline: Timeline) {
        if (timeline.nonEmpty) {
            AppWidgets.of(refEvents.updateAndGet { events: NotificationEvents -> events.clear(timeline) }).clearCounters().updateViews()
            clearAndroidNotifications()
        }
    }

    private fun clearAndroidNotifications() {
        NotificationEventType.validValues.forEach(Consumer { eventType: NotificationEventType -> clearAndroidNotification(eventType) })
    }

    fun clearAndroidNotification(eventType: NotificationEventType) {
        nM?.cancel(MyLog.APPTAG, eventType.notificationId())
    }

    fun update() {
        AppWidgets.of(refEvents.updateAndGet { obj: NotificationEvents -> obj.load() }).updateData().updateViews()
        if (notificationArea) {
            refEvents.get().map.values.stream().filter { data: NotificationData -> data.count > 0 }.forEach { data: NotificationData -> myContext.notify(data) }
        }
    }

    fun getAndroidNotification(data: NotificationData): Notification? {
        val contentText = (if (data.myActor.nonEmpty) data.myActor.actorNameInTimeline else myContext.context().getText(data.event.titleResId)).toString() + ": " + data.count
        MyLog.v(this, contentText)
        val builder = Notification.Builder(myContext.context())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(data.channelId())
        } else {
            if (data.event == NotificationEventType.SERVICE_RUNNING) {
                builder.setSound(null)
            } else {
                if (vibration) {
                    builder.setVibrate(VIBRATION_PATTERN)
                }
                builder.setSound(if (UriUtils.isEmpty(soundUri)) null else soundUri)
                builder.setLights(LIGHT_COLOR, 500, 1000)
            }
        }
        builder.setSmallIcon(if (data.event == NotificationEventType.SERVICE_RUNNING) R.drawable.ic_sync_white_24dp else if (SharedPreferencesUtil.getBoolean(MyPreferences.KEY_NOTIFICATION_ICON_ALTERNATIVE, false)) R.drawable.notification_icon_circle else R.drawable.notification_icon)
                .setContentTitle(myContext.context().getText(data.event.titleResId))
                .setContentText(contentText)
                .setWhen(data.updatedDate)
                .setShowWhen(true)
        builder.setContentIntent(data.getPendingIntent(myContext))
        return builder.build()
    }

    fun notifyAndroid(data: NotificationData) {
        nM?.let {
            createNotificationChannel(data)
            try {
                it.notify(MyLog.APPTAG, data.event.notificationId(), getAndroidNotification(data))
            } catch (e: Exception) {
                MyLog.w(this, "Notification failed", e)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(data: NotificationData) {
        if (nM == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channelId = data.channelId()
        val channelName = myContext.context().getText(data.event.titleResId)
        val description = "AndStatus, $channelName"
        val isSilent = data.event == NotificationEventType.SERVICE_RUNNING || UriUtils.isEmpty(soundUri)
        val channel = NotificationChannel(channelId, channelName,
                if (isSilent) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = description
        if (data.event == NotificationEventType.SERVICE_RUNNING) {
            channel.enableLights(false)
            channel.enableVibration(false)
        } else {
            channel.enableLights(true)
            channel.lightColor = LIGHT_COLOR
            if (vibration) {
                channel.vibrationPattern = VIBRATION_PATTERN
            }
        }
        channel.setSound(if (isSilent) null else soundUri, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        nM?.createNotificationChannel(channel)
    }

    fun initialize() {
        val stopWatch: StopWatch = StopWatch.createStarted()
        if (myContext.isReady()) {
            nM = myContext.context().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nM == null) {
                MyLog.w(this, "No Notification Service")
            }
        }
        notificationArea = NotificationMethodType.NOTIFICATION_AREA.isEnabled
        vibration = NotificationMethodType.VIBRATION.isEnabled
        soundUri = NotificationMethodType.SOUND.uri
        enabledEvents = NotificationEventType.validValues.stream()
                .filter { obj: NotificationEventType -> obj.isEnabled() }
                .collect(Collectors.toList())
                .also {
                    refEvents.set(NotificationEvents.of(myContext, it).load())
        }
        MyLog.i(this, "notifierInitializedMs:" + stopWatch.time + "; " + refEvents.get().size() + " events")
    }

    fun isEnabled(eventType: NotificationEventType?): Boolean {
        return enabledEvents.contains(eventType)
    }

    fun onUnsentActivity(activityId: Long) {
        if (activityId == 0L || !isEnabled(NotificationEventType.OUTBOX)) return
        MyProvider.setUnsentActivityNotification(myContext, activityId)
        update()
    }

    fun getEvents(): NotificationEvents {
        return refEvents.get()
    }

    companion object {
        private val VIBRATION_PATTERN: LongArray = longArrayOf(200, 300, 200, 300)
        private const val LIGHT_COLOR = Color.GREEN
    }
}