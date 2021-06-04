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
 * This is kind of mock of the concrete implementation
 * @author yvolk@yurivolkov.com
 */
class MyContextTestImpl internal constructor(parent: MyContext, context: Context, initializer: Any?) :
        MyContextImpl(parent, context, initializer) {
    private val assertionData: MutableMap<String, AssertionData> = ConcurrentHashMap()

    @Volatile
    private var httpConnectionMockClass: Class<out HttpConnection?>? = null

    @Volatile
    private var httpConnectionMockInstance: HttpConnection? = null

    @Volatile
    private var mockedConnectionState: ConnectionState = ConnectionState.UNKNOWN
    private val androidNotifications: MutableMap<NotificationEventType, NotificationData> = ConcurrentHashMap()

    fun setHttpConnectionMockClass(httpConnectionMockClass: Class<out HttpConnection?>?) {
        this.httpConnectionMockClass = httpConnectionMockClass
    }

    fun setHttpConnectionMockInstance(httpConnectionMockInstance: HttpConnection?) {
        this.httpConnectionMockInstance = httpConnectionMockInstance
    }

    override fun newInitialized(initializer: Any): MyContext {
        return MyContextTestImpl(this, context, initializer).initialize()
    }

    override fun isTestRun(): Boolean {
        return true
    }

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

    override fun getHttpConnectionMock(): HttpConnection? {
        if (httpConnectionMockInstance != null) return httpConnectionMockInstance
        return httpConnectionMockClass?.let { mockClass ->
            try {
                return mockClass.newInstance()
            } catch (e: InstantiationException) {
                MyLog.e(this, e)
            } catch (e: IllegalAccessException) {
                MyLog.e(this, e)
            }
            null
        }
    }

    override fun getConnectionState(): ConnectionState {
        return when (mockedConnectionState) {
            ConnectionState.UNKNOWN -> super.getConnectionState()
            else -> mockedConnectionState
        }
    }

    fun setConnectionState(connectionState: ConnectionState) {
        mockedConnectionState = connectionState
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
        return (instanceTag() + " http=" + getHttpConnectionMock() + ", "
                + super.toString())
    }

    override fun classTag(): String {
        return TAG
    }

    companion object {
        private val TAG: String = MyContextTestImpl::class.java.simpleName
    }

    init {
        if (parent is MyContextTestImpl) {
            assertionData.putAll(parent.assertionData)
            httpConnectionMockClass = parent.httpConnectionMockClass
            httpConnectionMockInstance = parent.httpConnectionMockInstance
            mockedConnectionState = parent.mockedConnectionState
            androidNotifications.putAll(parent.androidNotifications)
        }
    }
}
