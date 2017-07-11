package org.andstatus.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

public class FirstActivity extends AppCompatActivity {

    public enum NeedToStart {
        HELP,
        CHANGELOG,
        OTHER
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading);
        startNextActivity(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        startNextActivity(intent);
    }

    private void startNextActivity(Intent intent) {
        if (MyContextHolder.get().isReady() || MyContextHolder.get().state() == MyContextState.UPGRADING) {
            startNextActivitySync(MyContextHolder.get(), intent);
            finish();
        } else {
            MyContextHolder.initializeByFirstActivity(this);
        }
    }

    public void startNextActivitySync(MyContext myContext) {
        startNextActivitySync(myContext, getIntent());
    }

    private void startNextActivitySync(MyContext myContext, Intent myIntent) {
        switch (needToStartNext(this, myContext)) {
            case HELP:
                HelpActivity.startMe(this, true, false);
                break;
            case CHANGELOG:
                HelpActivity.startMe(this, true, true);
                break;
            default:
                Intent intent = new Intent(this, TimelineActivity.class);
                if (myIntent != null) {
                    String action = myIntent.getAction();
                    if (!TextUtils.isEmpty(action)) {
                        intent.setAction(action);
                    }
                    Uri data = myIntent.getData();
                    if (data != null) {
                        intent.setData(data);
                    }
                    Bundle extras = myIntent.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                startActivity(intent);
                break;
        }
    }

    public static NeedToStart needToStartNext(Context context, MyContext myContext) {
        NeedToStart startHelp = NeedToStart.OTHER;
        if (!myContext.isReady()) {
            MyLog.i(context, "Context is not ready: " + myContext.toString());
            startHelp = NeedToStart.HELP;
        } else if (myContext.persistentAccounts().isEmpty()) {
            MyLog.i(context, "No AndStatus Accounts yet");
            startHelp = NeedToStart.HELP;
        }
        if (myContext.isReady() && checkAndUpdateLastOpenedAppVersion(context, false)) {
            startHelp = NeedToStart.CHANGELOG;
        }
        return startHelp;
    }


    /**
     * @return true if we opened previous version
     */
    public static boolean checkAndUpdateLastOpenedAppVersion(Context context, boolean update) {
        boolean changed = false;
        long versionCodeLast =  SharedPreferencesUtil.getLong(MyPreferences.KEY_VERSION_CODE_LAST);
        PackageManager pm = context.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(context.getPackageName(), 0);
            int versionCode =  pi.versionCode;
            if (versionCodeLast < versionCode) {
                // Even if the User will see only the first page of the Help activity,
                // count this as showing the Change Log
                MyLog.v(HelpActivity.TAG, "Last opened version=" + versionCodeLast + ", current is " + versionCode
                        + (update ? ", updating" : "")
                );
                changed = true;
                if ( update && MyContextHolder.get().isReady()) {
                    SharedPreferencesUtil.putLong(MyPreferences.KEY_VERSION_CODE_LAST, versionCode);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.e(HelpActivity.TAG, "Unable to obtain package information", e);
        }
        return changed;
    }
}
