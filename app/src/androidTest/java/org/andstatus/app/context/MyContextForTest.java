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

import android.app.Notification;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.data.AssertionData;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.origin.PersistentOrigins;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.timeline.PersistentTimelines;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * This is kind of mock of the concrete implementation 
 * @author yvolk@yurivolkov.com
 */
public class MyContextForTest implements MyContext {
    private volatile MyContext myContext = null;
    private final Set<AssertionData> dataSet = new CopyOnWriteArraySet<>();
    private volatile Class<? extends HttpConnection> httpConnectionMockClass = null;
    private volatile HttpConnection httpConnectionMockInstance = null;
    private volatile ConnectionState mockedConnectionState = ConnectionState.UNKNOWN;
    private final Map<TimelineType, Notification> notifications = new ConcurrentHashMap<>();

    public MyContextForTest setContext(MyContext myContextIn) {
        MyContext myContext2 = myContextIn;
        for (int i = 0; i < 100; i++) {
            myContext = myContext2;
            if (myContext2 == null || !MyContextForTest.class.isAssignableFrom(myContext2.getClass())) {
                break;
            }
            myContext2 = ((MyContextForTest) myContext2).myContext;
        }
        if (myContext == null) {
            myContext = MyContextImpl.newEmpty(this);
        }
        return this;
    }

    public void setHttpConnectionMockClass(Class<? extends HttpConnection> httpConnectionMockClass) {
        this.httpConnectionMockClass = httpConnectionMockClass;
    }

    public void setHttpConnectionMockInstance(HttpConnection httpConnectionMockInstance) {
        this.httpConnectionMockInstance = httpConnectionMockInstance;
    }

    @Override
    public MyContext newInitialized(Context context, Object initializerName) {
        return new MyContextForTest().setContext(myContext.newInitialized(context, initializerName));
    }

    @Override
    public MyContext newCreator(Context context, Object initializerName) {
        return new MyContextForTest().setContext(myContext.newCreator(context, initializerName));
    }

    @Override
    public boolean initialized() {
        return myContext != null && myContext.initialized();
    }

    @Override
    public boolean isReady() {
        return myContext.isReady();
    }

    @Override
    public boolean isTestRun() {
        return true;
    }

    @Override
    public MyContextState state() {
        return myContext.state();
    }

    @Override
    public Context context() {
        if (myContext == null) {
            return null;
        } else {
            return myContext.context();
        }
    }

    @Override
    public String initializedBy() {
        return myContext.initializedBy();
    }

    @Override
    public long preferencesChangeTime() {
        return myContext.preferencesChangeTime();
    }

    @Override
    public DatabaseHolder getMyDatabase() {
        return myContext.getMyDatabase();
    }

    @Override
    public SQLiteDatabase getDatabase() {
        return myContext.getDatabase();
    }

    @Override
    public PersistentAccounts persistentAccounts() {
        return myContext.persistentAccounts();
    }

    @Override
    public void release() {
        myContext.release();
        dataSet.clear();
    }

    @Override
    public void put(AssertionData data) {
        dataSet.add(data);
    }
    
    public Set<AssertionData> getData() {
        return dataSet;
    }

    /**
     * Retrieves element from the set
     * Returns Empty data object if not found 
     * @return
     */
    public AssertionData takeDataByKey(String key) {
        AssertionData dataOut = AssertionData.getEmpty(key);
        for (AssertionData data : dataSet) {
            if (data.getKey().equals(key)) {
                dataOut = data;
                dataSet.remove(data);
                break;
            }
        }
        return dataOut;
    }

    @Override
    public boolean isExpired() {
        return myContext.isExpired();
    }

    @Override
    public void setExpired() {
        myContext.setExpired();
    }

    @Override
    public Locale getLocale() {
        return myContext.getLocale();
    }

    @Override
    public PersistentOrigins persistentOrigins() {
        return myContext.persistentOrigins();
    }

    @NonNull
    @Override
    public PersistentTimelines persistentTimelines() {
        return myContext.persistentTimelines();
    }

    @Override
    public HttpConnection getHttpConnectionMock() {
        if (httpConnectionMockInstance != null) {
            return httpConnectionMockInstance;
        } else if (httpConnectionMockClass != null) {
            try {
                return httpConnectionMockClass.newInstance();
            } catch (InstantiationException e) {
                MyLog.e(this, e);
            } catch (IllegalAccessException e) {
                MyLog.e(this, e);
            }
        }
        return null;
    }

    @Override
    public ConnectionState getConnectionState() {
        switch (mockedConnectionState) {
            case UNKNOWN:
                return myContext.getConnectionState();
            default:
                return mockedConnectionState;
        }
    }

    public void setConnectionState(ConnectionState connectionState) {
        this.mockedConnectionState = connectionState;
    }

    @Override
    public boolean isInForeground() {
        return myContext.isInForeground();
    }

    @Override
    public void setInForeground(boolean inForeground) {
        myContext.setInForeground(inForeground);
    }

	@Override
	public void notify(TimelineType id, Notification notification) {
		myContext.notify(id, notification);
		notifications.put(id, notification);
	}

	@Override
	public void clearNotification(TimelineType id) {
		myContext.clearNotification(id);
		notifications.remove(id);
	}

    @Override
    public long instanceId() {
        return myContext.instanceId();
    }

    public Map<TimelineType, Notification> getNotifications() {
		return notifications;
	}

    @Override
    public String toString() {
        return "MyContextForTest " + myContext;
    }
}
