/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.context

import android.content.ContentValues
import android.content.Context
import org.andstatus.app.data.AssertionData
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.service.ConnectionState
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyLog
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * This is kind of stub of the concrete implementation
 * @author yvolk@yurivolkov.com
 */
class MyContextTestImpl internal constructor(parent: MyContext, context: Context, initializer: Any?) :
        MyContextImpl(parent, context, initializer, MyContextTestImpl::class) {
    private val assertionData: MutableMap<String, AssertionData> = ConcurrentHashMap()

    @Volatile
    private var httpConnectionStubClass: Class<out HttpConnection?>? = null

    @Volatile
    private var httpConnectionStubInstance: HttpConnection? = null

    @Volatile
    override var connectionState: ConnectionState = parent.connectionState
        get() = when (field) {
            ConnectionState.UNKNOWN -> super.connectionState
            else -> field
        }

    private val androidNotifications: MutableMap<NotificationEventType, NotificationData> = ConcurrentHashMap()

    fun setHttpConnectionStubClass(httpConnectionStubClass: Class<out HttpConnection?>?) {
        this.httpConnectionStubClass = httpConnectionStubClass
    }

    fun setHttpConnectionStubInstance(httpConnectionStubInstance: HttpConnection?) {
        this.httpConnectionStubInstance = httpConnectionStubInstance
    }

    override fun newInstance(initializer: Any): MyContext {
        return MyContextTestImpl(this, context, initializer)
    }

    override val isTestRun: Boolean = true

    override fun release(reason: Supplier<String>) {
        super.release(reason)
        assertionData.clear()
    }

    override fun putAssertionData(key: String, contentValues: ContentValues) {
        assertionData[key] = AssertionData(key, contentValues)
    }

    fun getAssertions(): MutableCollection<AssertionData> {
        return assertionData.values
    }

    /**
     * @return Empty data object if not found
     */
    fun getAssertionData(key: String): AssertionData {
        return assertionData.getOrDefault(key, AssertionData.getEmpty(key))
    }

    override val httpConnectionStub: HttpConnection?
        get() {
            if (httpConnectionStubInstance != null) return httpConnectionStubInstance
            return httpConnectionStubClass?.let { stubClass ->
                try {
                    return stubClass.newInstance()
                } catch (e: InstantiationException) {
                    MyLog.e(this, e)
                } catch (e: IllegalAccessException) {
                    MyLog.e(this, e)
                }
                null
            }
        }

    override fun notify(data: NotificationData) {
        super.notify(data)
        androidNotifications[data.event] = data
    }

    override fun clearNotifications(timeline: Timeline) {
        super.clearNotifications(timeline)
        NotificationEventType.validValues.forEach { key: NotificationEventType? -> androidNotifications.remove(key) }
    }

    fun getAndroidNotifications(): MutableMap<NotificationEventType, NotificationData> {
        return androidNotifications
    }

    override fun toString(): String {
        return (instanceTag + " http=" + httpConnectionStub + ", "
                + super.toString())
    }

    override val appWidgetIds: List<Int>
        get() = super.appWidgetIds.ifEmpty { listOf(1) }

    init {
        if (parent is MyContextTestImpl) {
            assertionData.putAll(parent.assertionData)
            httpConnectionStubClass = parent.httpConnectionStubClass
            httpConnectionStubInstance = parent.httpConnectionStubInstance
            androidNotifications.putAll(parent.androidNotifications)
        }
    }
}
