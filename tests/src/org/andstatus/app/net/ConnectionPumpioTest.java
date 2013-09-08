package org.andstatus.app.net;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.origin.OAuthClientKeys;
import org.andstatus.app.origin.OriginConnectionData;

public class ConnectionPumpioTest extends InstrumentationTestCase {
    private static final String TAG = ConnectionPumpioTest.class.getSimpleName();
    ConnectionPumpio connection;
    OriginConnectionData connectionData;
    boolean initialized = false;
    
    private void initialize() {
        if (initialized) { 
            return;
        }
        Context targetContext = this.getInstrumentation().getTargetContext();
        if (targetContext == null) {
            Log.e(TAG, "targetContext is null.");
            throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
        }
        MyPreferences.initialize(targetContext, this);

        connectionData = new OriginConnectionData();
        connectionData.oauthClientKeys = OAuthClientKeys.fromOriginIdAndSharedPreferences(0, MyPreferences.getSharedPreferences("test"));
        connection = new ConnectionPumpio(connectionData);
        
        initialized = true;
    }
    
    public void test_oidToObjectType() {
        initialize();
        String oids[] = {"https://identi.ca/api/activity/L4v5OL93RrabouQc9_QGfg", 
                "https://identi.ca/api/comment/ibpUqhU1TGCE2yHNbUv54g",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "https://identi.ca/obj/ibpcomment",
                "http://identi.ca/notice/95772390",
                "http://identi.ca/user/46155"};
        String objectTypes[] = {"activity", 
                "comment", 
                "note",
                "unknown object type: https://identi.ca/obj/ibpcomment",
                "note",
                "person"};
        for (int ind=0; ind < oids.length; ind++) {
            String oid = oids[ind];
            String objectType = objectTypes[ind];
            assertEquals("Expecting '" + objectType + "'", objectType, connection.oidToObjectType(oid));
        }
    }

    public void test_usernameToHost() {
        initialize();
        String usernames[] = {"t131t@identi.ca", 
                "somebody@example.com",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "example.com",
                "@somewhere.com"};
        String hosts[] = {"identi.ca", 
                "example.com", 
                "",
                "",
                "somewhere.com"};
        for (int ind=0; ind < usernames.length; ind++) {
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], connection.usernameToHost(usernames[ind]));
        }
    }
}
