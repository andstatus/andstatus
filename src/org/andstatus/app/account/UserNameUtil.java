package org.andstatus.app.account;

import android.util.Log;

import org.andstatus.app.util.MyLog;

import java.util.regex.Pattern;

/**
 * @author yvolk
 */
class UserNameUtil {
    private static final String TAG = UserNameUtil.class.getSimpleName();
    private UserNameUtil() {}

    public static String fixUsername(String username_in) {
        String username = "";
        if (username_in != null) {
            username = username_in.trim();
        }
        if (!isUsernameValid(username)) {
            username = "";
        }
        return username;
    };

    public static boolean isUsernameValid(String username) {
        boolean ok = false;
        if (username != null && (username.length() > 0)) {
            ok = Pattern.matches("[a-zA-Z_0-9/\\.\\-\\(\\)]+", username);
            if (!ok && MyLog.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "The Username is not valid: \"" + username + "\"");
            }
        }
        return ok;
    }
    
}
