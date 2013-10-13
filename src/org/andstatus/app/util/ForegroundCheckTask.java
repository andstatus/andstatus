package org.andstatus.app.util;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.AsyncTask;

import java.util.List;

/**
 * Is your application is running in foreground?
 * 
 * @see <a
 *      href="http://stackoverflow.com/questions/3667022/android-is-application-running-in-background">stackoverflow.com/questions/3667022</a>
 */    
public class ForegroundCheckTask extends AsyncTask<Context, Void, Boolean> {
    private static final String TAG = ForegroundCheckTask.class.getSimpleName();

    @Override
    protected Boolean doInBackground(Context... params) {
        final Context context = params[0].getApplicationContext();
        return isAppOnForeground1(context);
    }

    private boolean isAppOnForeground1(Context context) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Is your application is running in foreground?
     * @param context
     * @return true in a case of error also! 
     */
    public static boolean isAppOnForeground(Context context) {
        boolean is = true;
        if (context == null) {
            MyLog.e(TAG, "Context is null.");
        } else
            try {
                is = ((new ForegroundCheckTask().execute(context).get()));
            } catch (Exception e) {
                // ignore
                is = true;
            }
        return is;
    }
}
