/**
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.account;

import android.util.Log;

import org.andstatus.app.net.Connection;
import org.andstatus.app.net.ConnectionBasicAuth;
import org.andstatus.app.net.ConnectionOAuth;

/**
 *  Originating (source) system (twitter.com, identi.ca, ... ) where messages are being created. 
 *  TODO: Currently the class is almost a stub and serves for ONE origin only :-)
 * @author yvolk
 *
 */
public class Origin {
    private static final String TAG = Origin.class.getSimpleName();

    /**
     * Default value for the Originating system mId.
     * TODO: Create a table of these "Origins" ?!
     */
    public static long ORIGIN_ID_DEFAULT = 1;
    /**
     * Name of the default Originating system (it is unique and permanent as an ID).
     */
    public static String ORIGIN_NAME_DEFAULT = "Twitter";

    private String mName = "";
    private long mId = 0;

    /**
     * Default OAuth setting
     */
    private boolean mOAuth = true;
    /**
     * Can OAuth connection setting can be turned on/off from the default setting
     * Starting from 2010-09 twitter.com allows OAuth only
     */
    private boolean mCanChangeOAuth = false;
    /**
     * Can user set username for the new user manually?
     * Current implementation of twitter.com authentication doesn't use this attribute, so it's disabled
     */
    private boolean mCanSetUsername = false;
    
    private Connection mConnection = null;
    
    public static Origin getOrigin(String name) {
        return new Origin(name);
    }
    
    public static Origin getOrigin(long id) {
        return new Origin(id);
    }
    
    /**
     * @return the Origin name, unique in the application
     */
    public String getName() {
        return mName;
    }

    /**
     * @return the OriginId in MyDatabase. 0 means that this system doesn't exist
     */
    public long getId() {
        return mId;
    }

    /**
     * Was this Origin stored for future reuse?
     */
    public boolean isPersistent() {
        return (getId() != 0);
    }
    
    /**
     * @return Default OAuth setting
     */
    public boolean isOAuth() {
        return mOAuth;
    }

    /**
     * @return the Can OAuth connection setting can be turned on/off from the default setting
     */
    public boolean canChangeOAuth() {
        return mCanChangeOAuth;
    }

    /**
     * @return Can app user set username for the new "Origin user" manually?
     */
    public boolean canSetUsername() {
        return mCanSetUsername;
    }

    /**
     * SharedPreferences - These preferences are per User
     */
     public Connection getConnection(MyAccount ma, boolean oauth) {
        if (mConnection != null) {
            if (mConnection.isOAuth() != oauth) {
                mConnection = null;
            }
        }
        if (mConnection == null) {
            if (ma == null) {
                Log.e(TAG, "MyAccount is null ??" );
            } else {
                if (oauth) {
                    mConnection = new ConnectionOAuth(ma);
                } else {
                    mConnection = new ConnectionBasicAuth(ma);
                }
            }
        }
        return mConnection;
    }

    private Origin(String name) {
        mName = name;
        // TODO: Persistence for Origins
        if (this.mName.compareToIgnoreCase(ORIGIN_NAME_DEFAULT) == 0) {
          mId = ORIGIN_ID_DEFAULT;
          mOAuth = true;
          mCanChangeOAuth = false;
          mCanSetUsername = false;
        }
    }
    
    private Origin(long id) {
       this (id == ORIGIN_ID_DEFAULT ? ORIGIN_NAME_DEFAULT : "");
    }
    
}
