/* 
 * Copyright (C) 2012-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.backup.DefaultProgressListener;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.backup.RestoreActivity;
import org.andstatus.app.context.ExecutionMode;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.ViewUtils;
import org.andstatus.app.widget.WebViewFragment;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class HelpActivity extends MyActivity {
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

    public static final int PAGE_CHANGELOG = 0;
    public static final int PAGE_USER_GUIDE = 1;
    public static final int PAGE_LOGO = 2;

    private ViewPager helpFlipper;
    /** Stores state of {@link #EXTRA_IS_FIRST_ACTIVITY} */
    private boolean mIsFirstActivity = false;
    private boolean wasPaused = false;
    private volatile ProgressLogger.ProgressListener progressListener = ProgressLogger.EMPTY_LISTENER;
    private static volatile boolean generatingDemoData = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.help;
        super.onCreate(savedInstanceState);
        if (isFinishing() || isCloseRequest(getIntent())) {
            return;
        }

        if (savedInstanceState != null) {
            mIsFirstActivity = savedInstanceState.getBoolean(EXTRA_IS_FIRST_ACTIVITY, false);
        }
        if (getIntent().hasExtra(EXTRA_IS_FIRST_ACTIVITY)) {
            mIsFirstActivity = getIntent().getBooleanExtra(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity);
        }

        if (myContextHolder.getNow().accounts().getCurrentAccount().nonValid()
                && myContextHolder.getExecutionMode() == ExecutionMode.ROBO_TEST
                && !generatingDemoData) {
            progressListener.cancel();

            generatingDemoData = true;
            progressListener = new DefaultProgressListener(this, R.string.app_name, "GenerateDemoData");
            demoData.addAsync(myContextHolder.getNow(), progressListener);
        }

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
        if (isFinishing()) {
            return true;
        }
        if (intent.hasExtra(EXTRA_CLOSE_ME)) {
            finish();
            return true;
        }
        return false;
    }

    private void showRestoreButton() {
        Button restoreButton = findViewById(R.id.button_restore);
        if (!generatingDemoData
                && myContextHolder.getNow().isReady() && myContextHolder.getNow().accounts().isEmpty()) {
            restoreButton.setOnClickListener(v -> {
                startActivity(new Intent(HelpActivity.this, RestoreActivity.class));
                finish();
            });
        } else {
            restoreButton.setVisibility(View.GONE);
        }
    }

    private void showGetStartedButton() {
        //The button is always visible in order to avoid a User's confusion,
        final Button getStarted = findViewById(R.id.button_help_get_started);
        getStarted.setVisibility(generatingDemoData ? View.GONE : View.VISIBLE);
        getStarted.setOnClickListener(v -> {
            if (myContextHolder.getFuture().isCompletedExceptionally()) {
                myContextHolder.initialize(this).thenStartApp();
                return;
            };
            switch (myContextHolder.getNow().state()) {
                case READY:
                    FirstActivity.checkAndUpdateLastOpenedAppVersion(HelpActivity.this, true);
                    if (myContextHolder.getNow().accounts().getCurrentAccount().isValid()) {
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
                    DialogFactory.showOkAlertDialog(HelpActivity.this, HelpActivity.this,
                            R.string.app_name, R.string.label_upgrading);
                    break;
                case DATABASE_UNAVAILABLE:
                    DialogFactory.showOkAlertDialog(HelpActivity.this, HelpActivity.this,
                            R.string.app_name, R.string.database_unavailable_description);
                    break;
                default:
                    DialogFactory.showOkAlertDialog(HelpActivity.this, HelpActivity.this,
                            R.string.app_name, R.string.loading);
                    break;
            }
        });
        if (myContextHolder.getNow().accounts().getCurrentAccount().isValid()) {
            getStarted.setText(R.string.button_skip);
        }
    }

    public static class LogoFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            final View view = inflater.inflate(R.layout.splash, container, false);
            showVersionText(inflater.getContext(), view);
            ViewUtils.showView(view, R.id.system_info_section, MyPreferences.isShowDebuggingInfoInUi()
                    || myContextHolder.getExecutionMode() != ExecutionMode.DEVICE);
            if (MyPreferences.isShowDebuggingInfoInUi()) {
                MyUrlSpan.showText(view, R.id.system_info,
                        myContextHolder.getSystemInfo(inflater.getContext(), false),
                        false, false);
            }

            return view;
        }

        private void showVersionText(Context context, @NonNull View parentView) {
            TextView versionText = parentView.findViewById(R.id.splash_application_version);
            MyStringBuilder text = MyStringBuilder.of(myContextHolder.getVersionText(context));
            if (!myContextHolder.getNow().isReady()) {
                text.append("\n" + myContextHolder.getNow().state());
                text.append("\n" + myContextHolder.getNow().getLastDatabaseError());
            }
            myContextHolder.tryNow().onFailure(e ->
                    text.append("\n\n " + e.getMessage() + "\n\n" + MyLog.getStackTrace(e))
            );

            versionText.setText(text.toString());
            versionText.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("http://andstatus.org"));
                startActivity(intent);
            });
        }
    }

    private void setupHelpFlipper() {
        helpFlipper = this.findViewById(R.id.help_flipper);
        helpFlipper.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {

            @Override
            public int getCount() {
                return 3;
            }

            @Override
            public Fragment getItem(int position) {
                switch (position) {
                    case PAGE_USER_GUIDE:
                        return WebViewFragment.from(R.raw.user_guide, R.raw.fb2_2_html);
                    case PAGE_CHANGELOG:
                        return WebViewFragment.from(R.raw.changes, R.raw.changes2html);
                    default:
                        return new LogoFragment();
                }
            }
        });
        

        if (ViewUtils.showView(this, R.id.button_help_learn_more, myContextHolder.getNow().isReady())) {
            final Button learnMore = findViewById(R.id.button_help_learn_more);
            learnMore.setOnClickListener(v -> {
                final PagerAdapter adapter = helpFlipper.getAdapter();
                if (adapter != null) {
                    helpFlipper.setCurrentItem(
                            helpFlipper.getCurrentItem() >= adapter.getCount() - 1
                                    ? 0
                                    : helpFlipper.getCurrentItem() + 1,
                            true);
                }
            });
        }

        if (getIntent().hasExtra(EXTRA_HELP_PAGE_INDEX)) {
            int pageToStart = getIntent().getIntExtra(EXTRA_HELP_PAGE_INDEX, 0);
            if (pageToStart > 0) {
                helpFlipper.setCurrentItem(pageToStart, true);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!generatingDemoData) {
            getMenuInflater().inflate(R.menu.help, menu);
        }
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
        myContextHolder.upgradeIfNeeded(this);
        if ( wasPaused && mIsFirstActivity
                &&  myContextHolder.getNow().accounts().getCurrentAccount().isValid() ) {
            // We assume that user pressed back after adding first account
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

    public static void startMe(Context context, boolean helpAsFirstActivity, int pageIndex) {
        Intent intent = new Intent(context, HelpActivity.class);
        if (helpAsFirstActivity) {
            intent.putExtra(HelpActivity.EXTRA_IS_FIRST_ACTIVITY, true);
        }
        intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_INDEX, pageIndex);
        if (context instanceof Activity) {
            context.startActivity(intent);
            MyLog.v(TAG, () -> "Finishing " + context.getClass().getSimpleName() + " and starting " + TAG);
            ((Activity) context).finish();
        } else {
            MyLog.v(TAG, () -> "Starting " + TAG + " from " + context.getApplicationContext().getClass().getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.getApplicationContext().startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        myContextHolder.getNow().setExpired(() -> "onRequestPermissionsResult");
        this.recreate();
    }

    @Override
    public void finish() {
        progressListener.onActivityFinish();
        generatingDemoData = false;
        super.finish();
    }

}
