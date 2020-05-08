/* 
 * Copyright (C) 2014-2019 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2010 Brion N. Emde, "BLOA" example, http://github.com/brione/Brion-Learns-OAuth
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

import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.FirstActivity;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.MyOAuth2AccessTokenJsonExtractor;
import org.andstatus.app.net.http.OAuthService;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.origin.PersistentOriginList;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.os.UiThreadExecutor;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyCheckBox;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.ViewUtils;
import org.andstatus.app.view.EnumSelector;

import java.util.List;
import java.util.Optional;

import io.vavr.control.NonFatalException;
import io.vavr.control.Try;
import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * Add new or edit existing account
 * 
 * @author yvolk@yurivolkov.com
 */
public class AccountSettingsActivity extends MyActivity {
    private static final String TAG = AccountSettingsActivity.class.getSimpleName();

    private enum ResultStatus {
        NONE,
        SUCCESS,
        ACCOUNT_INVALID,
        CONNECTION_EXCEPTION,
        CREDENTIALS_OF_OTHER_ACCOUNT
    }

    private static class TaskResult {
        final ResultStatus status;
        final CharSequence message;
        final Optional<Uri> whoAmI;
        @NonNull
        final Uri authUri;

        static TaskResult withWhoAmI(ResultStatus status, CharSequence message, Optional<Uri> whoAmI) {
            return new TaskResult(status, message, whoAmI, Uri.EMPTY);
        }

        static TaskResult withAuthUri(ResultStatus status, CharSequence message, Uri authUri) {
            return new TaskResult(status, message, Optional.empty(), authUri);
        }

        TaskResult(ResultStatus status) {
            this(status, "", Optional.empty(), Uri.EMPTY);
        }

        TaskResult(ResultStatus status, CharSequence message) {
            this(status, message, Optional.empty(), Uri.EMPTY);
        }

        private TaskResult(ResultStatus status, CharSequence message, Optional<Uri> whoAmI, Uri autUri) {
            this.status = status;
            this.message = message;
            this.whoAmI = whoAmI;
            this.authUri = autUri;
        }

        boolean isSuccess() {
            return status == ResultStatus.SUCCESS;
        }
    }

    private enum ActivityOnFinish {
        NONE,
        HOME,
        OUR_DEFAULT_SCREEN
    }
    private volatile ActivityOnFinish activityOnFinish = ActivityOnFinish.NONE;
    private volatile boolean initialSyncNeeded = false;
    
    private volatile StateOfAccountChangeProcess state = null;

    private StringBuilder mLatestErrorMessage = new StringBuilder();
    private boolean resumedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        resumedOnce = false;
        mLayoutId = R.layout.account_settings_main;
        super.onCreate(savedInstanceState);

        if (myContextHolder.initializeThenRestartMe(this)) {
            return;
        }

        if (savedInstanceState == null) {
            showFragment(AccountSettingsFragment.class);
        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private boolean isInvisibleView(@IdRes int id) {
        return !isVisibleView(id);
    }

    private boolean isVisibleView(@IdRes int id) {
        View view = findFragmentViewById(id);
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    private View findFragmentViewById(@IdRes int id) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentOne);
        if (fragment != null) {
            View view = fragment.getView();
            if (view != null) {
                return view.findViewById(id);
            }
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        restoreState(intent, "onNewIntent");
    }

    /**
     * Restore previous state and set the Activity mode depending on input (Intent).
     * We should decide if we should use the stored state or a newly created one
     */
    protected void restoreState(Intent intent, String calledFrom) {
        String message = "";
        if (state == null)  {
            state =  StateOfAccountChangeProcess.fromStoredState();
            message = (state.restored ? "Old state restored" : "No previous state");
        } else {
            message = "State existed and " + (state.restored ? "was restored earlier" : "was not restored earlier");
        }
        StateOfAccountChangeProcess newState = StateOfAccountChangeProcess.fromIntent(intent);
        if (state.actionCompleted || newState.useThisState) {
            message += "; New state";
            state = newState;
            if (state.originShouldBeSelected) {
                EnumSelector.newInstance(ActivityRequestCode.SELECT_ORIGIN_TYPE, OriginType.class).show(this);
            } else if (state.accountShouldBeSelected) {
                AccountSelector.selectAccountOfOrigin(this, ActivityRequestCode.SELECT_ACCOUNT, 0);
                message += "; Select account";
            }
            message += "; action=" + state.getAccountAction();

            updateScreen();
        }
        if (state.authenticatorResponse != null) {
            message += "; authenticatorResponse";
        }
        MyLog.v(this, "setState from " + calledFrom + "; " + message + "; intent=" + intent.toUri(0));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT:
                onAccountSelected(resultCode, data);
                break;
            case SELECT_ORIGIN_TYPE:
                onOriginTypeSelected(resultCode, data);
                break;
            case SELECT_ORIGIN:
                onOriginSelected(resultCode, data);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void onAccountSelected(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            AccountName accountName = AccountName.fromAccountName(myContextHolder.getNow(),
                    data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
            state.builder.rebuildMyAccount(accountName);
            if (!state.builder.isPersistent()) {
                mFinishing = true;
            }
        } else {
            mFinishing = true;
        }
        if (!mFinishing) {
            MyLog.v(this, "Switching to the selected account");
            state.builder.myContext().accounts().setCurrentAccount(state.builder.getAccount());
            state.setAccountAction(Intent.ACTION_EDIT);
            updateScreen();
        } else {
            MyLog.v(this, "No account supplied, finishing");
            finish();
        }
    }

    private void onOriginTypeSelected(int resultCode, Intent data) {
        OriginType originType = OriginType.UNKNOWN;
        if (resultCode == RESULT_OK) {
            originType = OriginType.fromCode(data.getStringExtra(IntentExtra.SELECTABLE_ENUM.key));
            if (originType.isSelectable()) {
                List<Origin> origins = myContextHolder.getNow().origins().originsOfType(originType);
                switch(origins.size()) {
                    case 0:
                        originType = OriginType.UNKNOWN;
                        break;
                    case 1:
                        onOriginSelected(origins.get(0));
                        break;
                    default:
                        selectOrigin(originType);
                        break;
                }
            }
        }
        if (!originType.isSelectable()) {
            closeAndGoBack();
        }
    }

    protected boolean selectOrigin(OriginType originType) {
        Intent intent = new Intent(AccountSettingsActivity.this, PersistentOriginList.class);
        intent.setAction(Intent.ACTION_INSERT);
        intent.putExtra(IntentExtra.ORIGIN_TYPE.key, originType.getCode());
        startActivityForResult(intent, ActivityRequestCode.SELECT_ORIGIN.id);
        return true;
    }

    private void onOriginSelected(int resultCode, Intent data) {
        Origin origin = Origin.EMPTY;
        if (resultCode == RESULT_OK) {
            origin = myContextHolder.getNow().origins().fromName(data.getStringExtra(IntentExtra.ORIGIN_NAME.key));
            if (origin.isPersistent()) {
                onOriginSelected(origin);
            }
        }
        if (!origin.isPersistent()) {
            closeAndGoBack();
        }
    }

    private void onOriginSelected(Origin origin) {
        if (state.getAccount().getOrigin().equals(origin)) return;

        // If we have changed the System, we should recreate the Account
        state.builder.setOrigin(origin);
        updateScreen();
        goToAddAccount();
    }

    private void goToAddAccount() {
        if (state.getAccountAction().equals(Intent.ACTION_INSERT)
                && isInvisibleView(R.id.uniqueName)
                && isInvisibleView(R.id.password)
                && isVisibleView(R.id.add_account)) {
            View addAccount = findFragmentViewById(R.id.add_account);
            if (addAccount != null) addAccount.performClick();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.remove_account_menu_id);
        if (item != null) {
            final boolean canRemove = state != null && state.builder != null && state.builder.isPersistent();
            item.setEnabled(canRemove);
            item.setVisible(canRemove);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                closeAndGoBack();
                return true;
            case R.id.preferences_menu_id:
                startMyPreferenceActivity();
                break;
            case R.id.remove_account_menu_id:
                DialogFactory.showOkCancelDialog(
                        getSupportFragmentManager().findFragmentById(R.id.fragmentOne),
                        R.string.remove_account_dialog_title,
                        R.string.remove_account_dialog_text, 
                        ActivityRequestCode.REMOVE_ACCOUNT);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startMyPreferenceActivity() {
        startActivity(new Intent(this, MySettingsActivity.class));
        finish();
    }
    
    private void updateScreen() {
        if (getSupportFragmentManager().findFragmentById(R.id.fragmentOne) == null) {
            MyLog.v(this, "No fragment found");
            return;
        }
        showTitle();
        showErrors();
        showOrigin();
        showUniqueName();
        showPassword();
        showAccountState();
        showAddAccountButton();
        ViewUtils.showView(this, R.id.below_add_account, state.builder.isPersistent());
        showHomeTimelineButton();
        showVerifyCredentialsButton();
        showIsDefaultAccount();
        showIsSyncedAutomatically();
        showSyncFrequency();
        showLastSyncSucceededDate();
    }

    private void showTitle() {
        MyAccount ma = state.getAccount();
        String title = getText(R.string.account_settings_activity_title).toString();
        if (ma.isValid()) {
            title += " - " + ma.getAccountName();
        }
        setTitle(title);
    }

    private void showErrors() {
        showTextView(R.id.latest_error_label, R.string.latest_error_label, 
                mLatestErrorMessage.length() > 0);
        showTextView(R.id.latest_error, mLatestErrorMessage, mLatestErrorMessage.length() > 0);
    }

    private void showOrigin() {
        TextView view = (TextView) findFragmentViewById(R.id.origin_name);
        if (view != null) {
            view.setText(this.getText(R.string.title_preference_origin_system)
                    .toString().replace("{0}", state.builder.getOrigin().getName())
                    .replace("{1}", state.builder.getOrigin().getOriginType().getTitle()));
        }
    }

    private void showUniqueName() {
        Origin origin = state.builder.getOrigin();
        showTextView(R.id.uniqueName_label, origin.hasHost()
                ? R.string.title_preference_username
                : R.string.username_at_your_server,
                state.builder.isPersistent() || state.isUsernameNeededToStartAddingNewAccount());
        EditText nameEditable = (EditText) findFragmentViewById(R.id.uniqueName);
        if (nameEditable != null) {
            if (state.builder.isPersistent() || !state.isUsernameNeededToStartAddingNewAccount()) {
                nameEditable.setVisibility(View.GONE);
            } else {
                nameEditable.setVisibility(View.VISIBLE);
                nameEditable.setHint(StringUtil.format(this,
                        origin.hasHost()
                                ? R.string.summary_preference_username
                                : R.string.summary_preference_username_webfinger_id,
                        origin.getName(),
                        origin.hasHost()
                                ? OriginType.SIMPLE_USERNAME_EXAMPLES
                                : origin.getOriginType().uniqueNameExamples
                ));
                nameEditable.addTextChangedListener(textWatcher);
                if (nameEditable.getText().length() == 0) {
                    nameEditable.requestFocus();
                }
            }
            String nameShown = StringUtil.nonEmptyNonTemp(state.getAccount().getUsername())
                    ? (origin.hasHost()
                        ? state.getAccount().getUsername()
                        : state.getAccount().getActor().getUniqueName())
                    : "";
            if (nameShown.compareTo(nameEditable.getText().toString()) != 0) {
                nameEditable.setText(nameShown);
            }
            showTextView(R.id.uniqueName_readonly, nameShown, state.builder.isPersistent());
        }
    }

    private TextWatcher textWatcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Empty
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Empty
        }

        @Override
        public void afterTextChanged(Editable s) {
            clearError();
        }
    };
    
    private void showPassword() {
        MyAccount ma = state.getAccount();
        boolean isNeeded = state.builder.getConnection().isPasswordNeeded() && !ma.isValidAndSucceeded();
        StringBuilder labelBuilder = new StringBuilder();
        if (isNeeded) {
            labelBuilder.append(this.getText(R.string.summary_preference_password));
            if (StringUtil.isEmpty(ma.getPassword())) {
                labelBuilder.append(": (" + this.getText(R.string.not_set) + ")");
            }
        }
        showTextView(R.id.password_label, labelBuilder.toString(), isNeeded);
        EditText passwordEditable = (EditText) findFragmentViewById(R.id.password);
        if (passwordEditable != null) {
            if (ma.getPassword().compareTo(passwordEditable.getText().toString()) != 0) {
                passwordEditable.setText(ma.getPassword());
            }
            passwordEditable.setVisibility(isNeeded ? View.VISIBLE : View.GONE);
            passwordEditable.setEnabled(!ma.isValidAndSucceeded());
            passwordEditable.addTextChangedListener(textWatcher);
        }
    }

    private void showAccountState() {
        MyAccount ma = state.getAccount();
        StringBuilder summary = null;
        if (state.builder.isPersistent()) {
            switch (ma.getCredentialsVerified()) {
                case SUCCEEDED:
                    summary = new StringBuilder(
                            this.getText(R.string.summary_preference_verify_credentials));
                    break;
                default:
                    if (state.builder.isPersistent()) {
                        summary = new StringBuilder(
                                this.getText(R.string.summary_preference_verify_credentials_failed));
                    } else {
                        if (state.builder.isOAuth()) {
                            summary = new StringBuilder(
                                    this.getText(R.string.summary_preference_add_account_oauth));
                        } else {
                            summary = new StringBuilder(
                                    this.getText(R.string.summary_preference_add_account_basic));
                        }
                    }
                    break;
            }
        }
        TextView stateText = (TextView) findFragmentViewById(R.id.account_state);
        if (stateText != null) {
            stateText.setText(summary);
        }
    }
    
    private void showAddAccountButton() {
        TextView textView = showTextView(R.id.add_account, null, !state.builder.isPersistent());
        if (textView != null && isVisibleView(R.id.add_account)) {
            textView.setOnClickListener(this::onAddAccountClick);
        }
    }

    void onAddAccountClick(View v) {
        clearError();
        updateChangedFields();
        updateScreen();
        CharSequence error = "";
        boolean addAccountEnabled = !state.isUsernameNeededToStartAddingNewAccount() ||
                state.getAccount().isUsernameValid();
        if (addAccountEnabled) {
            if (!state.builder.isOAuth() && StringUtil.isEmpty(state.builder.getPassword())) {
                addAccountEnabled = false;
                error = getText(R.string.title_preference_password);
            }
        } else {
            error = getText(state.builder.getOrigin().alternativeTermForResourceId(R.string.title_preference_username));
        }
        if (addAccountEnabled) {
            verifyCredentials(true);
        } else {
            appendError(getText(R.string.error_invalid_value) + ": " + error);
        }
    }

    private void clearError() {
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.removeAllCookie();
        if (mLatestErrorMessage.length() > 0) {
            mLatestErrorMessage.setLength(0);
            showErrors();
        }
    }

    private void showHomeTimelineButton() {
        TextView textView = showTextView(
                R.id.home_timeline, R.string.options_menu_home_timeline, state.builder.isPersistent());
        if (textView != null) {
            textView.setOnClickListener(v -> {
                updateChangedFields();
                activityOnFinish = ActivityOnFinish.HOME;
                finish();
            });
        }
    }

    private void showVerifyCredentialsButton() {
        TextView textView = showTextView(
                R.id.verify_credentials,
                state.getAccount().isValidAndSucceeded()
                        ? R.string.title_preference_verify_credentials
                        : R.string.title_preference_verify_credentials_failed,
                state.builder.isPersistent());
        if (textView != null) {
            textView.setOnClickListener(v -> {
                clearError();
                updateChangedFields();
                updateScreen();
                verifyCredentials(true);
            });
        }
    }
    
    private void showIsDefaultAccount() {
        boolean isDefaultAccount = state.getAccount().equals(state.builder.myContext().accounts().getDefaultAccount());
        View view= findFragmentViewById(R.id.is_default_account);
        if (view != null) {
            view.setVisibility(isDefaultAccount ? View.VISIBLE : View.GONE);
        }
    }

    private void showIsSyncedAutomatically() {
        MyCheckBox.set(findFragmentViewById(R.id.synced_automatically),
                state.builder.getAccount().isSyncedAutomatically(),
                (buttonView, isChecked) -> state.builder.setSyncedAutomatically(isChecked));
    }

    private void showSyncFrequency() {
        TextView label = (TextView) findFragmentViewById(R.id.label_sync_frequency);
        EditText view = (EditText) findFragmentViewById(R.id.sync_frequency);
        if (label != null && view != null) {
            String labelText = getText(R.string.sync_frequency_minutes).toString() + " " +
                    SharedPreferencesUtil.getSummaryForListPreference(this, Long.toString(MyPreferences.getSyncFrequencySeconds()),
                    R.array.fetch_frequency_values, R.array.fetch_frequency_entries,
                    R.string.summary_preference_frequency);
            label.setText(labelText);

            String value = state.builder.getAccount().getSyncFrequencySeconds() <= 0 ? "" :
                    Long.toString(state.builder.getAccount().getSyncFrequencySeconds() / 60);
            view.setText(value);
            view.setHint(labelText);
            view.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    // Empty
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // Empty
                }

                @Override
                public void afterTextChanged(Editable s) {
                    long value = StringUtil.toLong(s.toString());
                    state.builder.setSyncFrequencySeconds(value > 0 ? value * 60 : 0);
                }
            });
        }
    }

    private void showLastSyncSucceededDate() {
        long lastSyncSucceededDate = state.getAccount().getLastSyncSucceededDate();
        MyUrlSpan.showText((TextView) findFragmentViewById(R.id.last_synced),
                lastSyncSucceededDate == 0 ? getText(R.string.never).toString() :
                        RelativeTime.getDifference(this, lastSyncSucceededDate), TextMediaType.UNKNOWN, false, false);
    }

    private TextView showTextView(int textViewId, int textResourceId, boolean isVisible) {
        return showTextView(textViewId, textResourceId == 0 ? null : getText(textResourceId),
                isVisible);
    }
    
    private TextView showTextView(int textViewId, CharSequence text, boolean isVisible) {
        TextView textView = (TextView) findFragmentViewById(textViewId);
        if (textView != null) {
            if (!TextUtils.isEmpty(text)) {
                textView.setText(text);
            }
            textView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }
        return textView;
    }

    @Override
    protected void onResume() {
        super.onResume();
        myContextHolder.getNow().setInForeground(true);

        if (myContextHolder.initializeThenRestartMe(this)) {
            return;
        }
        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();

        updateScreen();

        Uri uri = getIntent().getData();
        if (uri != null) {
            if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
                MyLog.d(TAG, "uri=" + uri.toString());
            }
            if (HttpConnection.CALLBACK_URI.getScheme().equals(uri.getScheme())) {
                // To prevent repeating of this task
                getIntent().setData(null);
                // This activity was started by Twitter ("Service Provider")
                // so start second step of OAuth Authentication process
                new AsyncTaskLauncher<Uri>().execute(this, new OAuthAcquireAccessTokenTask(), uri);
                activityOnFinish = ActivityOnFinish.OUR_DEFAULT_SCREEN;
            }
        }
        resumedOnce = true;
    }

    /**
     * Verify credentials
     * 
     * @param reVerify true - Verify only if we didn't do this yet
     */
    private void verifyCredentials(boolean reVerify) {
        MyAccount ma = state.getAccount();
        if (reVerify || ma.getCredentialsVerified() == CredentialsVerificationStatus.NEVER) {
            MyServiceManager.setServiceUnavailable();
            MyServiceState state2 = MyServiceManager.getServiceState(); 
            if (state2 != MyServiceState.STOPPED) {
                MyServiceManager.stopService();
                if (state2 != MyServiceState.UNKNOWN) {
                    appendError(getText(R.string.system_is_busy_try_later) + " (" + state2 + ")");
                    return;
                }
            }
            if (ma.getCredentialsPresent()) {
                // Credentials are present, so we may verify them
                // This is needed even for OAuth - to know Username
                AsyncTaskLauncher.execute(this,
                        new VerifyCredentialsTask(ma.getActor().getEndpoint(ActorEndpointType.API_PROFILE)))
                        .onFailure( e -> appendError(e.getMessage()));
            } else {
                if (state.builder.isOAuth() && reVerify) {
                    // Credentials are not present,
                    // so start asynchronous OAuth Authentication process 
                    if (!ma.areClientKeysPresent()) {
                        AsyncTaskLauncher.execute(this, new OAuthRegisterClientTask())
                                .onFailure( e -> appendError(e.getMessage()));
                    } else {
                        AsyncTaskLauncher.execute(this, new OAuthAcquireRequestTokenTask(this))
                                .onFailure( e -> appendError(e.getMessage()));
                        activityOnFinish = ActivityOnFinish.OUR_DEFAULT_SCREEN;
                    }
                }
            }

        }
    }

    private void updateChangedFields() {
        if (!state.builder.isPersistent()) {
            EditText nameEditable = (EditText) findFragmentViewById(R.id.uniqueName);
            if (nameEditable != null) {
                String uniqueName = nameEditable.getText().toString();
                if (uniqueName.compareTo(state.getAccount().getOAccountName().getUniqueName()) != 0) {
                    state.builder.setUniqueName(uniqueName);
                }
            }
        }
        EditText passwordEditable = (EditText) findFragmentViewById(R.id.password);
        if (passwordEditable != null
                && state.getAccount().getPassword().compareTo(passwordEditable.getText().toString()) != 0) {
            state.builder.setPassword(passwordEditable.getText().toString());
        }
    }

    private void appendError(CharSequence errorMessage) {
        if (TextUtils.isEmpty(errorMessage)) {
            return;
        }
        if (mLatestErrorMessage.length()>0) {
            mLatestErrorMessage.append("/n");
        }
        mLatestErrorMessage.append(errorMessage);
        showErrors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        state.save();
        if (mFinishing && resumedOnce) {
            myContextHolder.setExpiredIfConfigChanged();
            if (activityOnFinish != ActivityOnFinish.NONE) {
                returnToOurActivity();
            }
        }
        myContextHolder.getNow().setInForeground(false);
    }

    private void returnToOurActivity() {
        myContextHolder
        .initialize(this)
        .whenSuccessAsync(myContext -> {
            MyLog.v(this, "Returning to " + activityOnFinish);
            MyAccount myAccount = myContext.accounts().fromAccountName(getState().getAccount().getAccountName());
            if (myAccount.isValid()) {
                myContext.accounts().setCurrentAccount(myAccount);
            }
            if (activityOnFinish == ActivityOnFinish.HOME) {
                Timeline home = myContext.timelines().get(TimelineType.HOME, myAccount.getActor(), Origin.EMPTY);
                TimelineActivity.startForTimeline(myContext, AccountSettingsActivity.this, home, true,
                        initialSyncNeeded);
                state.forget();
            } else {
                if (myContext.accounts().size() > 1) {
                    Intent intent = new Intent(myContext.context(), MySettingsActivity.class);
                    // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                } else {
                    TimelineActivity.goHome(AccountSettingsActivity.this);
                }
            }
        }, AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * This semaphore helps to avoid ripple effect: changes in MyAccount cause
     * changes in this activity ...
     */
    private boolean somethingIsBeingProcessed = false;
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            closeAndGoBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** 
     * Mark the action completed, close this activity and go back to the proper screen.
     * Return result to the caller if necessary.
     * See also com.android.email.activity.setup.AccountSetupBasics.finish() ...
     * 
     * @return
     */
    private void closeAndGoBack() {
        // Explicitly save MyAccount only on "Back key" 
        state.builder.save();
        String message = "";
        state.actionCompleted = true;
        activityOnFinish = ActivityOnFinish.OUR_DEFAULT_SCREEN;
        if (state.authenticatorResponse != null) {
            // We should return result back to AccountManager
            activityOnFinish = ActivityOnFinish.NONE;
            if (state.actionSucceeded) {
                if (state.builder.isPersistent()) {
                    // Pass the new/edited account back to the account manager
                    Bundle result = new Bundle();
                    result.putString(AccountManager.KEY_ACCOUNT_NAME, state.getAccount().getAccountName());
                    result.putString(AccountManager.KEY_ACCOUNT_TYPE,
                            AuthenticatorService.ANDROID_ACCOUNT_TYPE);
                    state.authenticatorResponse.onResult(result);
                    message += "authenticatorResponse; account.name=" + state.getAccount().getAccountName() + "; ";
                }
            } else {
                state.authenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
            }
        }
        // Forget old state
        state.forget();
        if (!mFinishing) {
            MyLog.v(this, "finish: action=" + state.getAccountAction() + "; " + message);
            finish();
        }
    }

    public static void startAddNewAccount(android.content.Context context) {
        Intent intent;
        intent = new Intent(context, AccountSettingsActivity.class);
        intent.setAction(Intent.ACTION_INSERT);
        context.startActivity(intent);
    }
    
    public StateOfAccountChangeProcess getState() {
        return state;
    }
    
    /**
     * Step 1 of 3 of the OAuth Authentication
     * Needed in a case we don't have the AndStatus Client keys for this Microblogging system
     */
    private class OAuthRegisterClientTask extends MyAsyncTask<Void, Void, TaskResult> {
        private ProgressDialog dlg;

        OAuthRegisterClientTask() {
            super(PoolEnum.LONG_UI);
        }

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(AccountSettingsActivity.this,
                    getText(R.string.dialog_title_registering_client),
                    getText(R.string.dialog_summary_registering_client),
                // duration indeterminate
                    true, 
                // not cancelable
                    false); 
        }

        @Override
        protected TaskResult doInBackground2(Void aVoid) {
            boolean succeeded = false;
            String connectionErrorMessage = "";
            try {
                if (!state.getAccount().areClientKeysPresent()) {
                    state.builder.registerClient();
                } 
                if (state.getAccount().areClientKeysPresent()) {
                    state.builder.getOriginConfig();
                    succeeded = true;
                }
            } catch (ConnectionException e) {
                connectionErrorMessage = e.getMessage();
                MyLog.i(this, e);
            }

            String stepErrorMessage = "";
            if (!succeeded) {
                stepErrorMessage = AccountSettingsActivity.this
                        .getString(R.string.client_registration_failed);
                if (!StringUtil.isEmpty(connectionErrorMessage)) {
                    stepErrorMessage += ": " + connectionErrorMessage;
                }
                MyLog.d(TAG, stepErrorMessage);
            }
            return new TaskResult(succeeded ? ResultStatus.SUCCESS : ResultStatus.NONE, stepErrorMessage);
        }

        // This is in the UI thread, so we can mess with the UI
        @Override
        protected void onPostExecute2(TaskResult result) {
            DialogFactory.dismissSafely(dlg);
            if (result != null && !AccountSettingsActivity.this.isFinishing()) {
                if (result.isSuccess()) {
                    state.builder.myContext().setExpired(() -> "Client registered");
                    myContextHolder
                    .initialize(AccountSettingsActivity.this, this)
                    .whenSuccessAsync(myContext -> {
                        state.builder.rebuildMyAccount(myContext);
                        updateScreen();
                        AsyncTaskLauncher.execute(this,
                                new OAuthAcquireRequestTokenTask(AccountSettingsActivity.this))
                                .onFailure( e -> appendError(e.getMessage()));
                        activityOnFinish = ActivityOnFinish.OUR_DEFAULT_SCREEN;
                    }, UiThreadExecutor.INSTANCE);
                } else {
                    appendError(result.message);
                    state.builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED);
                    updateScreen();
                }
            }
            MyLog.v(this, I18n.succeededText(result != null && result.isSuccess()));
        }
    }

    /**
     * Task 2 of 3 required for OAuth Authentication.
     * See http://www.snipe.net/2009/07/writing-your-first-twitter-application-with-oauth/
     * for good OAuth Authentication flow explanation.
     *  
     * During this task:
     * 1. AndStatus ("Consumer") Requests "Request Token" from Twitter ("Service provider"), 
     * 2. Waits for that Request Token
     * 3. Consumer directs a User to the Service Provider: opens Twitter site in Internet Browser window
     *    in order to Obtain User Authorization.
     * 4. This task ends.
     * 
     * What will occur later:
     * 5. After User Authorized AndStatus in the Internet Browser,
     *    Twitter site will redirect User back to
     *    AndStatus and then the second OAuth task will start.
     *   
     * @author yvolk@yurivolkov.com This code is based on "BLOA" example,
     *         http://github.com/brione/Brion-Learns-OAuth yvolk: I had to move
     *         this code from OAuthActivity here in order to be able to show
     *         ProgressDialog and to get rid of any "Black blank screens"
     */
    private static class OAuthAcquireRequestTokenTask extends MyAsyncTask<Void, Void, TaskResult> {
        private final AccountSettingsActivity activity;
        private ProgressDialog dlg;

        OAuthAcquireRequestTokenTask(AccountSettingsActivity activity) {
            super(PoolEnum.LONG_UI);
            this.activity = activity;
        }

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(activity,
                    activity.getText(R.string.dialog_title_acquiring_a_request_token),
                    activity.getText(R.string.dialog_summary_acquiring_a_request_token),
                    // indeterminate duration
                    true, 
                    // not cancelable
                    false 
                    );
        }

        @Override
        protected TaskResult doInBackground2(Void aVoid) {
            String stepErrorMessage = "";
            String connectionErrorMessage = "";
            Uri authUri = Uri.EMPTY;
            try {
                Connection connection = activity.state.builder.getConnection();
                MyLog.v(this, "Retrieving request token for " + connection);
                OAuthService oAuthService = connection.getOAuthService();
                if (oAuthService == null) {
                    connectionErrorMessage = "No OAuth service for " + connection;
                } else if ( !connection.areOAuthClientKeysPresent()) {
                    connectionErrorMessage = "No Client keys for " + connection;
                } else {
                    if (oAuthService.isOAuth2()) {
                        final OAuth20Service service = oAuthService.getService(true);
                        authUri = UriUtils.fromString(service.getAuthorizationUrl());
                    } else {
                        OAuthConsumer consumer = oAuthService.getConsumer();

                        // This is really important. If you were able to register your
                        // real callback Uri with Twitter, and not some fake Uri
                        // like I registered when I wrote this example, you need to send
                        // null as the callback Uri in this function call. Then
                        // Twitter will correctly process your callback redirection
                        authUri = UriUtils.fromString(oAuthService.getProvider()
                                .retrieveRequestToken(consumer, HttpConnection.CALLBACK_URI.toString()));
                        activity.state.setRequestTokenWithSecret(consumer.getToken(), consumer.getTokenSecret());
                    }

                    // This is needed in order to complete the process after redirect
                    // from the Browser to the same activity.
                    activity.state.actionCompleted = false;
                }
            } catch (OAuthMessageSignerException | OAuthNotAuthorizedException
                    | OAuthExpectationFailedException
                    | OAuthCommunicationException
                    | ConnectionException e) {
                connectionErrorMessage = e.getMessage();
                MyLog.i(this, e);
                authUri = Uri.EMPTY;
            }

            ResultStatus resultStatus = UriUtils.isDownloadable(authUri)
                    ? ResultStatus.SUCCESS : ResultStatus.CONNECTION_EXCEPTION;
            if (resultStatus != ResultStatus.SUCCESS) {
                stepErrorMessage = activity.getString(R.string.acquiring_a_request_token_failed);
                if (StringUtil.nonEmpty(connectionErrorMessage)) {
                    stepErrorMessage += ": " + connectionErrorMessage;
                }
                MyLog.d(TAG, stepErrorMessage);

                activity.state.builder.clearClientKeys();
            }
            return TaskResult.withAuthUri(resultStatus, stepErrorMessage, authUri);
        }

        // This is in the UI thread, so we can mess with the UI
        @Override
        protected void onPostExecute2(TaskResult result) {
            DialogFactory.dismissSafely(dlg);
            if (result != null && !activity.isFinishing()) {
                if (result.isSuccess()) {
                    activity.activityOnFinish = ActivityOnFinish.NONE;

                    MyLog.d(activity, "Starting Web view at " + result.authUri);
                    Intent i = new Intent(activity, AccountSettingsWebActivity.class);
                    i.putExtra(AccountSettingsWebActivity.EXTRA_URLTOOPEN, result.authUri.toString());
                    activity.startActivity(i);

                    // Finish this activity in order to start properly
                    // after redirection from Browser
                    // Because of initializations in onCreate...
                    activity.finish();
                } else {
                    activity.appendError(result.message);
                    activity.state.builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED);
                    activity.updateScreen();
                }
            }
            MyLog.v(this, I18n.succeededText(result != null && result.isSuccess()));
        }
    }
    
    /**
     * Task 3 of 3 required for OAuth Authentication.
     *  
     * During this task:
     * 1. AndStatus ("Consumer") exchanges "Request Token", 
     *    obtained earlier from Twitter ("Service provider"),
     *    for "Access Token". 
     * 2. Stores the Access token for all future interactions with Twitter.
     * 
     * @author yvolk@yurivolkov.com This code is based on "BLOA" example,
     *         http://github.com/brione/Brion-Learns-OAuth yvolk: I had to move
     *         this code from OAuthActivity here in order to be able to show
     *         ProgressDialog and to get rid of any "Black blank screens"
     */
    private class OAuthAcquireAccessTokenTask extends MyAsyncTask<Uri, Void, TaskResult> {
        private ProgressDialog dlg;

        OAuthAcquireAccessTokenTask() {
            super(PoolEnum.LONG_UI);
        }

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(AccountSettingsActivity.this,
                    getText(R.string.dialog_title_acquiring_an_access_token),
                    getText(R.string.dialog_summary_acquiring_an_access_token),
                 // indeterminate duration
                    true, 
                 // not cancelable
                    false 
                    ); 
        }

        @Override
        protected TaskResult doInBackground2(Uri uri) {
            String message = "";
            String accessToken = "";
            String accessSecret = "";
            Optional<Uri> whoAmI = Optional.empty();

            if (state.getAccount().getOAuthService() == null) {
                message = "Connection is not OAuth";
                MyLog.e(this, message);
            } else {
                // We don't need to worry about any saved states: we can reconstruct the state
                if (uri != null && HttpConnection.CALLBACK_URI.getHost() != null &&
                        HttpConnection.CALLBACK_URI.getHost().equals(uri.getHost())) {

                    state.builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER);
                    try {
                        if (state.getAccount().getOAuthService().isOAuth2()) {
                            String authCode = uri.getQueryParameter("code");
                            MyLog.d(this, "Auth code is: " + authCode);
                            final OAuth20Service service = state.getAccount().getOAuthService().getService(true);
                            final OAuth2AccessToken token = service.getAccessToken(authCode);
                            accessToken = token.getAccessToken();
                            accessSecret = token.getRawResponse();
                            whoAmI = MyOAuth2AccessTokenJsonExtractor.extractWhoAmI(accessSecret);
                        } else {
                            String requestToken = state.getRequestToken();
                            String requestSecret = state.getRequestSecret();
                            // Clear the request stuff, we've used it already
                            state.setRequestTokenWithSecret(null, null);

                            OAuthConsumer consumer = state.getAccount().getOAuthService().getConsumer();
                            if (!(requestToken == null || requestSecret == null)) {
                                consumer.setTokenWithSecret(requestToken, requestSecret);
                            }
                            String oauthToken = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
                            String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);

                            /*
                             * yvolk 2010-07-08: It appeared that this may be not true:
                             * Assert.assertEquals(otoken, mConsumer.getToken()); (e.g.
                             * if User denied access during OAuth...) hence this is not
                             * Assert :-)
                             */
                            if (oauthToken != null || consumer.getToken() != null) {
                                state.getAccount().getOAuthService().getProvider()
                                        .retrieveAccessToken(consumer, verifier);
                                // Now we can retrieve the goodies
                                accessToken = consumer.getToken();
                                accessSecret = consumer.getTokenSecret();
                            }
                        }
                    } catch (Exception e) {
                        message = e.getMessage();
                        MyLog.i(this, e);
                    } finally {
                        state.builder.setUserTokenWithSecret(accessToken, accessSecret);
                        MyLog.d(this, "Access token for " + state.getAccount().getAccountName() +
                                ": " + accessToken + ", " + accessSecret);
                    }
                }
            }
            return TaskResult.withWhoAmI(
                    StringUtil.nonEmpty(accessToken) && StringUtil.nonEmpty(accessSecret)
                            ? ResultStatus.SUCCESS : ResultStatus.CREDENTIALS_OF_OTHER_ACCOUNT,
                    message,
                    whoAmI
            );
        }

        // This is in the UI thread, so we can mess with the UI
        @Override
        protected void onPostExecute2(TaskResult result) {
            DialogFactory.dismissSafely(dlg);
            if (result != null && !AccountSettingsActivity.this.isFinishing()) {
                if (result.isSuccess()) {
                    // Credentials are present, so we may verify them
                    // This is needed even for OAuth - to know Twitter Username
                    AsyncTaskLauncher.execute(this, new VerifyCredentialsTask(result.whoAmI))
                            .onFailure( e -> appendError(e.getMessage()));
                } else {
                    String stepErrorMessage = AccountSettingsActivity.this
                        .getString(R.string.acquiring_an_access_token_failed) +
                            (StringUtil.nonEmpty(result.message) ? ": " + result.message : "");
                    appendError(stepErrorMessage);
                    state.builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED);
                    updateScreen();
                }
            }
            MyLog.v(this, I18n.succeededText(result != null && result.isSuccess()));
        }
    }

    /**
     * Assuming we already have credentials to verify, verify them
     * @author yvolk@yurivolkov.com
     */
    private class VerifyCredentialsTask extends MyAsyncTask<Void, Void, TaskResult> {
        private ProgressDialog dlg;
        private volatile boolean skip = false;
        private final Optional<Uri> whoAmI;

        VerifyCredentialsTask(Optional<Uri> whoAmI) {
            super(PoolEnum.LONG_UI);
            this.whoAmI = whoAmI;
        }

        @Override
        protected void onPreExecute() {
            activityOnFinish = ActivityOnFinish.NONE;
            dlg = ProgressDialog.show(AccountSettingsActivity.this,
                    getText(R.string.dialog_title_checking_credentials),
                    getText(R.string.dialog_summary_checking_credentials),
                 // indeterminate duration
                    true, 
                 // not cancelable
                    false 
                    );

            synchronized (AccountSettingsActivity.this) {
                if (somethingIsBeingProcessed) {
                    skip = true;
                } else {
                    somethingIsBeingProcessed = true;
                }
            }
        }

        @Override
        protected TaskResult doInBackground2(Void aVoid) {
            if (skip) return new TaskResult(ResultStatus.NONE);

            return Try.success(state.builder)
            .flatMap(MyAccount.Builder::getOriginConfig)
            .flatMap(b -> b.getConnection().verifyCredentials(whoAmI))
            .flatMap(state.builder::onCredentialsVerified)
            .map(MyAccount.Builder::getAccount)
            .filter(MyAccount::isValidAndSucceeded)
            .onSuccess( myAccount -> {
                state.forget();
                MyContext myContext = myContextHolder.initialize(AccountSettingsActivity.this, this).getBlocking();
                FirstActivity.checkAndUpdateLastOpenedAppVersion(AccountSettingsActivity.this, true);

                final Timeline timeline = myContext.timelines().forUser(TimelineType.HOME, myAccount.getActor());
                if (timeline.isTimeToAutoSync()) {
                    initialSyncNeeded = true;
                    activityOnFinish = ActivityOnFinish.HOME;
                }
            })
            .map(ma -> new TaskResult(ResultStatus.SUCCESS, ""))
            .recover(ConnectionException.class, e -> {
                ResultStatus status = ResultStatus.ACCOUNT_INVALID;
                String message = "";
                switch (e.getStatusCode()) {
                    case AUTHENTICATION_ERROR:
                        status = ResultStatus.ACCOUNT_INVALID;
                        break;
                    case CREDENTIALS_OF_OTHER_ACCOUNT:
                        status = ResultStatus.CREDENTIALS_OF_OTHER_ACCOUNT;
                        break;
                    default:
                        status = ResultStatus.CONNECTION_EXCEPTION;
                        break;
                }
                message = e.toString();
                MyLog.v(this, e);
                return new TaskResult(status, message);
            })
            .recover(NonFatalException.class, e -> new TaskResult(ResultStatus.CONNECTION_EXCEPTION, e.getMessage()))
            .get();
        }

        /**
         * Credentials were verified just now!
         */
        @Override
        protected void onPostExecute2(TaskResult resultIn) {
            DialogFactory.dismissSafely(dlg);
            if (AccountSettingsActivity.this.isFinishing()) return;

            TaskResult result = resultIn == null ? new TaskResult(ResultStatus.NONE) : resultIn;
            CharSequence errorMessage = "";
            switch (result.status) {
                case SUCCESS:
                    Toast.makeText(AccountSettingsActivity.this, R.string.authentication_successful,
                            Toast.LENGTH_SHORT).show();
                    break;
                case ACCOUNT_INVALID:
                    errorMessage = getText(R.string.dialog_summary_authentication_failed);
                    break;
                case CREDENTIALS_OF_OTHER_ACCOUNT:
                    errorMessage = getText(R.string.error_credentials_of_other_user);
                    break;
                case CONNECTION_EXCEPTION:
                    errorMessage = getText(R.string.error_connection_error) + " \n" + result.message;
                    MyLog.i(this, errorMessage.toString());
                    break;
                default:
                    break;
            }
            if (activityOnFinish == ActivityOnFinish.HOME) {
                if (isMyResumed()) {
                    finish();
                } else {
                    finish();
                    returnToOurActivity();
                }
                return;
            }
            if (!skip) {
                StateOfAccountChangeProcess state2 = AccountSettingsActivity.this.state;
                // Note: MyAccount was already saved inside MyAccount.verifyCredentials
                // Now we only have to deal with the state
               
                state2.actionSucceeded = result.isSuccess();
                if (result.isSuccess()) {
                    state2.actionCompleted = true;
                    if (state2.getAccountAction().compareTo(Intent.ACTION_INSERT) == 0) {
                        state2.setAccountAction(Intent.ACTION_EDIT);
                    }
                }
                somethingIsBeingProcessed = false;
            }
            updateScreen();
            appendError(errorMessage);
        }
    }
}
