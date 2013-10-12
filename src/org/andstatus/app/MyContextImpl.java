/**
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

package org.andstatus.app;

import android.content.Context;
import android.util.Log;

import net.jcip.annotations.ThreadSafe;

import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

/**
 * Contains global state of the application
 * The objects are effectively immutable
 * @author yvolk@yurivolkov.com
 */
@ThreadSafe
public final class MyContextImpl implements MyContext {
    private static final String TAG = MyContextImpl.class.getSimpleName();

    private MyContextState state = MyContextState.EMPTY;
    /**
     * Single context object for which we will request SharedPreferences
     */
    private Context context = null;
    /**
     * Name of the object that initialized the class
     */
    private String initializedBy;
    /**
     * When preferences, loaded into this class, were changed
     */
    private long preferencesChangeTime = 0;
    private MyDatabase db;
    PersistentAccounts persistentAccounts ;
    
    private MyContextImpl() {};

    @Override
    public MyContext newInitialized(Context context, String initializerName) {
        MyContextImpl newMyContext = getCreator(context, initializerName);
        if ( newMyContext.context != null) {
            Log.v(TAG, "Starting initialization by " + initializerName);
            newMyContext.preferencesChangeTime = MyPreferences.getPreferencesChangeTime();
            MyDatabase newDb = new MyDatabase(newMyContext.context);
            newMyContext.state = newDb.checkState();
            switch (newMyContext.state) {
                case READY:
                    newMyContext.db = newDb;
                    newMyContext.persistentAccounts = PersistentAccounts.initialize(newMyContext.context);
                    break;
                default: 
                    newMyContext.persistentAccounts = PersistentAccounts.getEmpty();
            }
        }

        MyLog.v(TAG, "Initialized by " + newMyContext.initializedBy + " state=" + newMyContext.state + (newMyContext.context == null ? "; no context" : "; context: " + newMyContext.context.getClass().getName()));
        return newMyContext;
    }
    
    @Override
    public MyContext newCreator(Context context, String initializerName) {
        MyContextImpl newMyContext = getCreator(context, initializerName);
        MyLog.v(TAG, "newCreator by " + newMyContext.initializedBy 
                + (newMyContext.context == null ? "" : " context: " + newMyContext.context.getClass().getName()));
        return newMyContext;
    }

    private MyContextImpl getCreator(Context context, String initializerName) {
        MyContextImpl newMyContext = getEmpty();
        newMyContext.initializedBy = initializerName;
        if (context != null) {
            // Maybe we should use context_in.getApplicationContext() ??
            newMyContext.context = context.getApplicationContext();
        
            /* This may be useful to know from where the class was initialized
            StackTraceElement[] elements = Thread.currentThread().getStackTrace(); 
            for(int i=0; i<elements.length; i++) { 
                Log.v(TAG, elements[i].toString()); 
            }
            */
        
            if ( newMyContext.context == null) {
                Log.v(TAG, "getApplicationContext is null, trying the context_in itself: " + context.getClass().getName());
                newMyContext.context = context;
            }
        }
        return newMyContext;
    }
    
    public static MyContextImpl getEmpty() {
        MyContextImpl myContext = new MyContextImpl();
        myContext.persistentAccounts = PersistentAccounts.getEmpty();
        return myContext;
    }

    @Override
    public boolean initialized() {
        return (state != MyContextState.EMPTY && state != MyContextState.ERROR);
    }

    @Override
    public boolean isReady() {
        return (state == MyContextState.READY);
    }

    @Override
    public MyContextState state() {
        return state;
    }
    
    @Override
    public Context context() {
        return context;
    }

    @Override
    public String initializedBy() {
        return initializedBy;
    }
    
    @Override
    public long preferencesChangeTime() {
        return preferencesChangeTime;
    }
    
    @Override
    public MyDatabase getDatabase() {
        return db;
    }

    @Override
    public void release() {
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing database " + e.getMessage());
            }
        }
        MyLog.forget();
    }

    @Override
    public PersistentAccounts persistentAccounts() {
        return persistentAccounts;
    }
}
