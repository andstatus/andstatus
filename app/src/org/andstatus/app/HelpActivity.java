/* 
 * Copyright (C) 2012-2014 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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

import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.backup.RestoreActivity;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.util.ActivitySwipeDetector;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SwipeInterface;
import org.andstatus.app.util.Xslt;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

/**
 * @author yvolk@yurivolkov.com
 * @author Torgny 
 */
public class HelpActivity extends Activity implements SwipeInterface {

    // Constants
    public static final String TAG = "HelpActivity";

    /**
     * integer - Index of Help screen to show first
     */
    public static final String EXTRA_HELP_PAGE_INDEX = ClassInApplicationPackage.PACKAGE_NAME + ".HELP_PAGE_ID";
    /**
     * boolean - If the activity is the first then we should provide means 
     * to start {@link TimelineActivity} from this activity
     */
    public static final String EXTRA_IS_FIRST_ACTIVITY = ClassInApplicationPackage.PACKAGE_NAME + ".IS_FIRST_ACTIVITY";

    public static final int PAGE_INDEX_DEFAULT = 0;
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
        super.onCreate(savedInstanceState);

        MyContextHolder.initialize(this, this);
        if (MyPreferences.shouldSetDefaultValues()) {
            // Default values for the preferences will be set only once
            // and in one place: here
            MyPreferences.setDefaultValues(R.xml.preferences, false);
            if (MyPreferences.shouldSetDefaultValues()) {
                MyLog.e(this, "Default values were not set?!");   
            } else {
                MyLog.i(this, "Default values has been set");   
            }
        }
        
        MyPreferences.setThemedContentView(this, R.layout.help);

        if (savedInstanceState != null) {
            mIsFirstActivity = savedInstanceState.getBoolean(EXTRA_IS_FIRST_ACTIVITY, false);
        }
        if (getIntent().hasExtra(EXTRA_IS_FIRST_ACTIVITY)) {
            mIsFirstActivity = getIntent().getBooleanExtra(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity);
        }

        TextView versionText = (TextView) findViewById(R.id.splash_application_version);
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            versionText.setText(pi.packageName + " v." + pi.versionName + " (" + pi.versionCode + ")");
        } catch (NameNotFoundException e) {
            MyLog.e(this, "Unable to obtain package information", e);
        }

        // Show the Change log
        Xslt.toWebView(this, R.id.help_changelog, R.raw.changes, R.raw.changes2html);
        
        versionText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://github.com/andstatus/andstatus/wiki"));
                startActivity(intent);
            }
        });

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
        
        //The button is always visible in order to avoid a User's confusion,
        final Button getStarted = (Button) findViewById(R.id.button_help_get_started);
        getStarted.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MyContextHolder.get().isReady()) {
                    MyPreferences.checkAndUpdateLastOpenedAppVersion(HelpActivity.this, true);
                    if (MyContextHolder.get().persistentAccounts().getCurrentAccount().isValid()) {
                        startActivity(new Intent(HelpActivity.this, TimelineActivity.class));
                    } else {
                        startActivity(new Intent(HelpActivity.this, AccountSettingsActivity.class));
                    }
                }
                finish();
            }
        });

        setupHelpFlipper();
    }

    private void setupHelpFlipper() {
        mFlipper = ((ViewFlipper) this.findViewById(R.id.help_flipper));
        
        // In order to have swipe gestures we need to add listeners to every page
        // Only in case of WebView (changelog) we need to set listener on than WebView,
        // not on its parent: ScrollView
        ActivitySwipeDetector swipe = new ActivitySwipeDetector(this, this);
        for (int ind = 0; ind < mFlipper.getChildCount()-1; ind++ ) {
            mFlipper.getChildAt(ind).setOnTouchListener(swipe);
        }
        View view = findViewById(R.id.help_changelog);
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
        super.onCreateOptionsMenu(menu);
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.help, menu);
        return true;
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
            MyLog.i(activity, "Context is not ready");
            helpAsFirstActivity = true;
        } else if (MyPreferences.shouldSetDefaultValues()) {
            MyLog.i(activity, "We are running the Application for the very first time?");
            helpAsFirstActivity = true;
        } else if (MyContextHolder.get().persistentAccounts().isEmpty()) {
            MyLog.i(activity, "No AndStatus Accounts yet");
            if (!(activity instanceof AccountSettingsActivity)) {
                helpAsFirstActivity = true;
            }
        } 
        
        // Show Change Log after update
        if (MyPreferences.checkAndUpdateLastOpenedAppVersion(activity, true)) {
            showChangeLog = true;                    
        }

        boolean doFinish = helpAsFirstActivity || showChangeLog;
        if (doFinish) {
            Intent intent = new Intent(activity, HelpActivity.class);
            if (helpAsFirstActivity) {
                intent.putExtra(HelpActivity.EXTRA_IS_FIRST_ACTIVITY, true);
            } 
            
            int pageIndex = PAGE_INDEX_DEFAULT;
            if (MyContextHolder.get().isReady() && MyContextHolder.get().persistentAccounts().isEmpty()) {
                pageIndex = PAGE_INDEX_USER_GUIDE;
            } else if (showChangeLog) {
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
}
