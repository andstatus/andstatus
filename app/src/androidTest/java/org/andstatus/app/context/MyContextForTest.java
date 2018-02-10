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

package org.andstatus.app.context;

import android.content.ContentValues;
import android.content.Context;
import android.support.annotation.NonNull;

import org.andstatus.app.data.AssertionData;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.notification.NotificationData;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is kind of mock of the concrete implementation 
 * @author yvolk@yurivolkov.com
 */
public class MyContextForTest extends MyContextImpl {
    private final Map<String, AssertionData> assertionData = new ConcurrentHashMap<>();
    private volatile Class<? extends HttpConnection> httpConnectionMockClass = null;
    private volatile HttpConnection httpConnectionMockInstance = null;
    private volatile ConnectionState mockedConnectionState = ConnectionState.UNKNOWN;
    private final Map<NotificationEventType, NotificationData> androidNotifications = new ConcurrentHashMap<>();

    MyContextForTest(MyContextForTest parent, Context context, Object initializer) {
        super(parent, context, initializer);
        if (parent != null) {
            assertionData.putAll(parent.assertionData);
            httpConnectionMockClass = parent.httpConnectionMockClass;
            httpConnectionMockInstance = parent.httpConnectionMockInstance;
            mockedConnectionState = parent.mockedConnectionState;
            androidNotifications.putAll(parent.androidNotifications);
        }
    }

    public void setHttpConnectionMockClass(Class<? extends HttpConnection> httpConnectionMockClass) {
        this.httpConnectionMockClass = httpConnectionMockClass;
    }

    public void setHttpConnectionMockInstance(HttpConnection httpConnectionMockInstance) {
        this.httpConnectionMockInstance = httpConnectionMockInstance;
    }

    @Override
    public MyContext newInitialized(Object initializer) {
        return new MyContextForTest(this, context(), initializer).initialize();
    }

    @Override
    public MyContext newCreator(Context context, Object initializer) {
        return new MyContextForTest(null, context, initializer);
    }

    @Override
    public boolean isTestRun() {
        return true;
    }

    @Override
    public void release() {
        super.release();
        assertionData.clear();
    }

    @Override
    public void putAssertionData(@NonNull String key, @NonNull ContentValues contentValues) {
        assertionData.put(key, new AssertionData(key, contentValues));
    }
    
    public Collection<AssertionData> getAssertions() {
        return assertionData.values();
    }

    /**
     * @return Empty data object if not found
     */
    public AssertionData getAssertionData(String key) {
        return assertionData.getOrDefault(key, AssertionData.getEmpty(key));
    }

    @Override
    public HttpConnection getHttpConnectionMock() {
        if (httpConnectionMockInstance != null) return httpConnectionMockInstance;
        if (httpConnectionMockClass == null) return null;
        try {
            return httpConnectionMockClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            MyLog.e(this, e);
        }
        return null;
    }

    @Override
    public ConnectionState getConnectionState() {
        switch (mockedConnectionState) {
            case UNKNOWN:
                return super.getConnectionState();
            default:
                return mockedConnectionState;
        }
    }

    public void setConnectionState(ConnectionState connectionState) {
        this.mockedConnectionState = connectionState;
    }

    @Override
	public void notify(NotificationData data) {
		super.notify(data);
		androidNotifications.put(data.event, data);
	}

	@Override
	public void clearNotification(@NonNull Timeline timeline) {
		super.clearNotification(timeline);
        NotificationEventType.validValues.stream().filter(event -> event.isShownOn(timeline.getTimelineType()))
                .forEach(androidNotifications::remove);
	}

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    public Map<NotificationEventType, NotificationData> getAndroidNotifications() {
		return androidNotifications;
	}

    @Override
    public String toString() {
        return MyLog.getInstanceTag(this) + " http=" + getHttpConnectionMock() + ", "
                + super.toString();
    }
}
