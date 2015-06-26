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

import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.data.AssersionData;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.origin.PersistentOrigins;
import org.andstatus.app.service.ConnectionRequired;

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
    private MyContext myContext;
    private Set<AssersionData> dataSet = new CopyOnWriteArraySet<AssersionData>();
    private HttpConnection httpConnection;
    private ConnectionRequired mOnline = ConnectionRequired.ANY; 
    private Map<TimelineType, Notification> notifications = new ConcurrentHashMap<TimelineType, Notification>();

    public MyContextForTest setContext(MyContext myContextIn) {
        myContext = myContextIn;
        return this;
    }

    public void setHttpConnectionMock(HttpConnection httpConnection) {
        this.httpConnection = httpConnection;
    }
    
    @Override
    public MyContext newInitialized(Context context, String initializerName) {
        return new MyContextForTest().setContext(myContext.newInitialized(context, initializerName));
    }

    @Override
    public MyContext newCreator(Context context, String initializerName) {
        return new MyContextForTest().setContext(myContext.newCreator(context, initializerName));
    }

    @Override
    public boolean initialized() {
        if (myContext == null) {
            return false;
        } else {
            return myContext.initialized();
        }
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
    public MyDatabase getDatabase() {
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
    public void put(AssersionData data) {
        dataSet.add(data);
    }
    
    public Set<AssersionData> getData() {
        return dataSet;
    }

    /**
     * Retrieves element from the set
     * Returns Empty data object if not found 
     * @return
     */
    public AssersionData takeDataByKey(String key) {
        AssersionData dataOut = AssersionData.getEmpty(key);
        for (AssersionData data : dataSet) {
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

    @Override
    public HttpConnection getHttpConnectionMock() {
        return httpConnection;
    }

    @Override
    public boolean isOnline(ConnectionRequired connectionRequired) {
        switch (mOnline) {
            case ANY:
                return myContext.isOnline(connectionRequired);
            default:
                switch (connectionRequired) {
                    case ONLINE:
                        return (mOnline == ConnectionRequired.ONLINE || mOnline == ConnectionRequired.WIFI);
                    case WIFI:
                        return (mOnline == ConnectionRequired.WIFI);
                    case OFFLINE:
                        return (mOnline == ConnectionRequired.OFFLINE);
                    default:
                        return true;
                }
        }
    }

    public void setOnline(ConnectionRequired connectionState) {
        this.mOnline = connectionState;
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
	
	public Map<TimelineType, Notification> getNotifications() {
		return notifications;
	}
}
