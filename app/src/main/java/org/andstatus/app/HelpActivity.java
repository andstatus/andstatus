/* 
 * Copyright (C) 2012-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.backup.RestoreActivity;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.util.ActivitySwipeDetector;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.SwipeInterface;
import org.andstatus.app.util.Xslt;

public class HelpActivity extends MyActivity implements SwipeInterface {

    // Constants
    public static final String TAG = HelpActivity.class.getSimpleName();

    /**
     * integer - Index of Help screen to show first
     */
    public static final String EXTRA_HELP_PAGE_INDEX = ClassInApplicationPackage.PACKAGE_NAME + ".HELP_PAGE_ID";
    /**
     * boolean - If the activity is the first then we should provide means 
     * to start {@link TimelineActivity} from this activity
     */
    public static final String EXTRA_IS_FIRST_ACTIVITY = ClassInApplicationPackage.PACKAGE_NAME + ".IS_FIRST_ACTIVITY";
    public static final String EXTRA_CLOSE_ME = ClassInApplicationPackage.PACKAGE_NAME + ".CLOSE_ME";

    public static final int PAGE_INDEX_LOGO = 0;
    public static final int PAGE_INDEX_USER_GUIDE = 1;
    public static final int PAGE_INDEX_CHANGELOG = 2;
    
    // Local objects
    private ViewFlipper mFlipper;
    /**
     * Stores state of {@link #EXTRA_IS_FIRST_ACTIVITY}
     */
    private boolean mIsFirstActivity = false;
    private boolean wasPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MyContextHolder.initialize(this, this);

        mLayoutId = R.layout.help;
        super.onCreate(savedInstanceState);

        if (isCloseRequest(getIntent())) {
            return;
        }

        if (savedInstanceState != null) {
            mIsFirstActivity = savedInstanceState.getBoolean(EXTRA_IS_FIRST_ACTIVITY, false);
        }
        if (getIntent().hasExtra(EXTRA_IS_FIRST_ACTIVITY)) {
            mIsFirstActivity = getIntent().getBooleanExtra(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity);
        }

        showVersionText();
        MyUrlSpan.showText(this, R.id.system_info, MyContextHolder.getSystemInfo(this, false), false, false);
        showChangeLog();
        showUserGuide();
        showRestoreButton();
        showGetStartedButton();
        setupHelpFlipper();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        isCloseRequest(intent);
    }

    private boolean isCloseRequest(Intent intent) {
        if (intent.hasExtra(EXTRA_CLOSE_ME)) {
            finish();
            return true;
        }
        return false;
    }

    private void showVersionText() {
        TextView versionText = (TextView) findViewById(R.id.splash_application_version);
        String text = MyContextHolder.getVersionText(this);
        if (!MyContextHolder.get().isReady()) {
            text += "\n" + MyContextHolder.get().state();
        }
        versionText.setText(text);
        versionText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("http://andstatus.org"));
                startActivity(intent);
            }
        });
    }

    private void showChangeLog() {
        Xslt.toWebView(this, R.id.changelog, R.raw.changes, R.raw.changes2html);
    }

    private void showUserGuide() {
        Xslt.toWebView(this, R.id.user_guide, R.raw.user_guide, R.raw.fb2_2_html);
    }
    
    private void showRestoreButton() {
        Button restoreButton = (Button) findViewById(R.id.button_restore);
        if (MyContextHolder.get().isReady() && MyContextHolder.get().persistentAccounts().isEmpty()) {
            restoreButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(HelpActivity.this, RestoreActivity.class));
                    finish();
                }
            });
        } else {
            restoreButton.setVisibility(View.GONE);
        }
    }

    private void showGetStartedButton() {
        //The button is always visible in order to avoid a User's confusion,
        final Button getStarted = (Button) findViewById(R.id.button_help_get_started);
        getStarted.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (MyContextHolder.get().state()) {
                    case READY:
                        checkAndUpdateLastOpenedAppVersion(HelpActivity.this, true);
                        if (MyContextHolder.get().persistentAccounts().getCurrentAccount().isValid()) {
                            startActivity(new Intent(HelpActivity.this, TimelineActivity.class));
                        } else {
                            startActivity(new Intent(HelpActivity.this, AccountSettingsActivity.class));
                        }
                        finish();
                        break;
                    case NO_PERMISSIONS:
                        // Actually this is not used for now...
                        Permissions.checkPermissionAndRequestIt( HelpActivity.this,
                                Permissions.PermissionType.GET_ACCOUNTS);
                        break;
                    case UPGRADING:
                        DialogFactory.newNoActionAlertDialog(HelpActivity.this,
                                R.string.app_name, R.string.label_upgrading);
                        break;
                    default:
                        DialogFactory.newNoActionAlertDialog(HelpActivity.this,
                                R.string.app_name, R.string.loading);
                        break;
                }
            }
        });
        if (MyContextHolder.get().persistentAccounts().getCurrentAccount().isValid()) {
            getStarted.setText(R.string.button_skip);
        }
    }

    private void setupHelpFlipper() {
        mFlipper = ((ViewFlipper) this.findViewById(R.id.help_flipper));
        
        // In order to have swipe gestures we need to add listeners to every page
        // Only in a case of WebView we need to set a listener on than WebView,
        // not on its parent ScrollView
        ActivitySwipeDetector swipe = new ActivitySwipeDetector(this, this);
        for (int ind = 0; ind < mFlipper.getChildCount()-1; ind++ ) {
            mFlipper.getChildAt(ind).setOnTouchListener(swipe);
        }
        View view = findViewById(R.id.changelog);
        view.setOnTouchListener(swipe);
        view = findViewById(R.id.user_guide);
        view.setOnTouchListener(swipe);
        
        final Button learnMore = (Button) findViewById(R.id.button_help_learn_more);
        learnMore.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFlipper.showNext();
            }
        });
        
        if (getIntent().hasExtra(EXTRA_HELP_PAGE_INDEX)) {
            int pageToStart = getIntent().getIntExtra(EXTRA_HELP_PAGE_INDEX, 0);
            if (pageToStart > 0) {
                mFlipper.setDisplayedChild(pageToStart);
            }
        }
        
        AlphaAnimation anim = (AlphaAnimation) AnimationUtils.loadAnimation(HelpActivity.this, R.anim.fade_in);
        mFlipper.startAnimation(anim);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.help, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.preferences_menu_id:
                startActivity(new Intent(this, MySettingsActivity.class));
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyContextHolder.upgradeIfNeeded(this);
        // We assume that user pressed back after adding first account
        if ( wasPaused && mIsFirstActivity 
                &&  MyContextHolder.get().persistentAccounts().getCurrentAccount().isValid() ) {
            Intent intent = new Intent(this, TimelineActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasPaused = true;
    }

    @Override
    public void onLeftToRight(View v) {
        if (mFlipper != null) {
            mFlipper.showPrevious();
        }
    }

    @Override
    public void onRightToLeft(View v) {
        if (mFlipper != null) {
            mFlipper.showNext();
        }
    }

    /**
     * @return true if calling Activity is being finishing
     */
    public static boolean startFromActivity(Activity activity) {
        boolean helpAsFirstActivity = false;
        boolean showChangeLog = false;
        if (!MyContextHolder.get().isReady()) {
            MyLog.i(activity, "Context is not ready: " + MyContextHolder.get().toString());
            helpAsFirstActivity = true;
        } else if (MyContextHolder.get().persistentAccounts().isEmpty()) {
            MyLog.i(activity, "No AndStatus Accounts yet");
            if (!(activity instanceof AccountSettingsActivity)) {
                helpAsFirstActivity = true;
            }
        } 
        
        // Show Change Log after update
        if (MyContextHolder.get().isReady() && checkAndUpdateLastOpenedAppVersion(activity, true)) {
            showChangeLog = true;                    
        }

        boolean doFinish = helpAsFirstActivity || showChangeLog;
        if (doFinish) {
            Intent intent = new Intent(activity, HelpActivity.class);
            if (helpAsFirstActivity) {
                intent.putExtra(HelpActivity.EXTRA_IS_FIRST_ACTIVITY, true);
            } 
            
            int pageIndex = PAGE_INDEX_LOGO;
            if (!helpAsFirstActivity && showChangeLog) {
                pageIndex = PAGE_INDEX_CHANGELOG;
            }
            intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_INDEX, pageIndex);
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Context context = activity.getApplicationContext();
            MyLog.v(TAG, "Finishing " + activity.getClass().getSimpleName() + " and starting " + TAG);
            activity.finish();
            context.startActivity(intent);
        }
        return doFinish;
    }

    /**
     * @return true if we opened previous version
     */
    public static boolean checkAndUpdateLastOpenedAppVersion(Context context, boolean update) {
        boolean changed = false;
        int versionCodeLast =  SharedPreferencesUtil.getDefaultSharedPreferences().getInt(MyPreferences.KEY_VERSION_CODE_LAST, 0);
        PackageManager pm = context.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(context.getPackageName(), 0);
            int versionCode =  pi.versionCode;
            if (versionCodeLast < versionCode) {
                // Even if the User will see only the first page of the Help activity,
                // count this as showing the Change Log
                MyLog.v(TAG, "Last opened version=" + versionCodeLast + ", current is " + versionCode
                        + (update ? ", updating" : "")
                );
                changed = true;
                if ( update && MyContextHolder.get().isReady()) {
                    SharedPreferencesUtil.getDefaultSharedPreferences().edit()
                            .putInt(MyPreferences.KEY_VERSION_CODE_LAST, versionCode).commit();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.e(TAG, "Unable to obtain package information", e);
        }
        return changed;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        MyContextHolder.get().setExpired();
        this.recreate();
    }

}
