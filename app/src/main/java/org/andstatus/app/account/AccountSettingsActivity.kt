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
package org.andstatus.app.account

import android.accounts.AccountManager
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IdRes
import io.vavr.control.NonFatalException
import io.vavr.control.Try
import oauth.signpost.OAuth
import oauth.signpost.exception.OAuthCommunicationException
import oauth.signpost.exception.OAuthExpectationFailedException
import oauth.signpost.exception.OAuthMessageSignerException
import oauth.signpost.exception.OAuthNotAuthorizedException
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.FirstActivity
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.http.HttpConnectionInterface
import org.andstatus.app.net.http.MyOAuth2AccessTokenJsonExtractor
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.origin.PersistentOriginList
import org.andstatus.app.origin.SIMPLE_USERNAME_EXAMPLES
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.os.UiThreadExecutor
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyCheckBox
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.ViewUtils
import org.andstatus.app.view.EnumSelector
import java.util.*

/**
 * Add new or edit existing account
 *
 * @author yvolk@yurivolkov.com
 */
class AccountSettingsActivity : MyActivity() {
    private enum class ResultStatus {
        NONE, SUCCESS, ACCOUNT_INVALID, CONNECTION_EXCEPTION, CREDENTIALS_OF_OTHER_ACCOUNT
    }

    internal enum class FragmentAction {
        ON_ORIGIN_SELECTED, NONE;

        fun toBundle(args: Bundle): Bundle {
            args.putString(FRAGMENT_ACTION_KEY, name)
            return args
        }

        companion object {
            val FRAGMENT_ACTION_KEY: String = "fragment_action"
            fun fromBundle(args: Bundle?): FragmentAction {
                if (args == null) return NONE
                val value = args.getString(FRAGMENT_ACTION_KEY)
                for (action in values()) {
                    if (action.name == value) return action
                }
                return NONE
            }
        }
    }

    private class TaskResult private constructor(val status: ResultStatus?, val message: CharSequence?,
                                                 val whoAmI: Optional<Uri>, val authUri: Uri) {

        constructor(status: ResultStatus?) : this(status, "", Optional.empty<Uri>(), Uri.EMPTY) {}
        constructor(status: ResultStatus?, message: CharSequence?) :
                this(status, message, Optional.empty<Uri>(), Uri.EMPTY) {
        }

        fun isSuccess(): Boolean {
            return status == ResultStatus.SUCCESS
        }

        companion object {
            fun withWhoAmI(status: ResultStatus?, message: CharSequence?, whoAmI: Optional<Uri>): TaskResult {
                return TaskResult(status, message, whoAmI, Uri.EMPTY)
            }

            fun withAuthUri(status: ResultStatus?, message: CharSequence?, authUri: Uri): TaskResult {
                return TaskResult(status, message, Optional.empty(), authUri)
            }
        }
    }

    private enum class ActivityOnFinish {
        NONE, HOME, OUR_DEFAULT_SCREEN
    }

    @Volatile
    private var activityOnFinish: ActivityOnFinish? = ActivityOnFinish.NONE

    @Volatile
    private var initialSyncNeeded = false

    @Volatile
    private var state: StateOfAccountChangeProcess = StateOfAccountChangeProcess.EMPTY
    private val mLatestErrorMessage: StringBuilder = StringBuilder()
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        resumedOnce = false
        mLayoutId = R.layout.account_settings_main
        super.onCreate(savedInstanceState)
        if (restartMeIfNeeded()) return
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        restoreState(intent, "onActivityCreated")
    }

    private fun isInvisibleView(@IdRes id: Int): Boolean {
        return !isVisibleView(id)
    }

    private fun isVisibleView(@IdRes id: Int): Boolean {
        val view = findFragmentViewById(id)
        return view != null && view.visibility == View.VISIBLE
    }

    fun findFragmentViewById(@IdRes id: Int): View? {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentOne)
        if (fragment != null) {
            val view = fragment.view
            if (view != null) {
                return view.findViewById(id)
            }
        }
        return null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoreState(intent, "onNewIntent")
    }

    /**
     * Restore previous state and set the Activity mode depending on input (Intent).
     * We should decide if we should use the stored state or a newly created one
     */
    protected fun restoreState(intent: Intent, calledFrom: String?) {
        var message: String
        if (state.isEmpty) {
            state = StateOfAccountChangeProcess.fromStoredState()
            message = if (state.restored) "Old state restored" else "No previous state"
        } else {
            message = "State existed and " + if (state.restored) "was restored earlier" else "was not restored earlier"
        }
        val newState: StateOfAccountChangeProcess = StateOfAccountChangeProcess.fromIntent(intent)
        if (state.actionCompleted || newState.useThisState) {
            message += "; " +
                    (if (state.actionCompleted) "Old completed, " else "") +
                    (if (newState.useThisState) "Using this: " else "") +
                    "New state"
            state = newState
            if (state.originShouldBeSelected) {
                EnumSelector.newInstance<OriginType>(ActivityRequestCode.SELECT_ORIGIN_TYPE, OriginType::class.java)
                        .show(this)
            } else if (state.accountShouldBeSelected) {
                AccountSelector.selectAccountOfOrigin(this, ActivityRequestCode.SELECT_ACCOUNT, 0)
                message += "; Select account"
            } else if (state.getAccountAction() == Intent.ACTION_INSERT && myAccount.origin.originType === OriginType.MASTODON) {
                showFragment(InstanceForNewAccountFragment::class.java, Bundle())
            } else {
                showFragment(AccountSettingsFragment::class.java, Bundle())
            }
            message += "; action=" + state.getAccountAction()
        } else {
            showFragment(AccountSettingsFragment::class.java, Bundle())
        }
        if (state.authenticatorResponse != null) {
            message += "; authenticatorResponse"
        }
        MyLog.v(this, "restoreState from " + calledFrom + "; " + message + "; intent=" + intent.toUri(0))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data == null) super.onActivityResult(requestCode, resultCode, data)
        else when (ActivityRequestCode.fromId(requestCode)) {
            ActivityRequestCode.SELECT_ACCOUNT -> onAccountSelected(resultCode, data)
            ActivityRequestCode.SELECT_ORIGIN_TYPE -> onOriginTypeSelected(resultCode, data)
            ActivityRequestCode.SELECT_ORIGIN -> onOriginSelected(resultCode, data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onAccountSelected(resultCode: Int, data: Intent) {
        val toFinish: Boolean
        val builder = state.builder

        toFinish = if (resultCode == RESULT_OK) {
            val accountName: AccountName = AccountName.fromAccountName(MyContextHolder.myContextHolder.getNow(),
                    data.getStringExtra(IntentExtra.ACCOUNT_NAME.key))
            builder.rebuildMyAccount(accountName)
            !builder.isPersistent()
        } else {
            true
        }
        if (toFinish) {
            MyLog.v(this, "No account supplied, finishing")
            finish()
        } else {
            MyLog.v(this, "Switching to the selected account")
            builder.myContext().accounts().setCurrentAccount(builder.getAccount())
            state.setAccountAction(Intent.ACTION_EDIT)
            updateScreen()
        }
    }

    private fun onOriginTypeSelected(resultCode: Int, data: Intent) {
        var originType = OriginType.UNKNOWN
        if (resultCode == RESULT_OK) {
            originType = OriginType.fromCode(data.getStringExtra(IntentExtra.SELECTABLE_ENUM.key))
            if (originType.isSelectable()) {
                val origins: MutableList<Origin> = MyContextHolder.myContextHolder.getNow()
                        .origins().originsOfType(originType)
                when (origins.size) {
                    0 -> originType = OriginType.UNKNOWN
                    1 -> onOriginSelected(origins[0])
                    else -> selectOrigin(originType)
                }
            }
        }
        if (!originType.isSelectable()) {
            closeAndGoBack()
        }
    }

    fun selectOrigin(originType: OriginType) {
        if (originType === OriginType.MASTODON) {
            val args = Bundle()
            args.putString(IntentExtra.ORIGIN_TYPE.key, originType.getCode())
            showFragment(InstanceForNewAccountFragment::class.java, args)
        } else {
            val intent = Intent(this@AccountSettingsActivity, PersistentOriginList::class.java)
            intent.action = Intent.ACTION_INSERT
            intent.putExtra(IntentExtra.ORIGIN_TYPE.key, originType.getCode())
            startActivityForResult(intent, ActivityRequestCode.SELECT_ORIGIN.id)
        }
    }

    private fun onOriginSelected(resultCode: Int, data: Intent) {
        var origin: Origin = Origin.EMPTY
        if (resultCode == RESULT_OK) {
            origin = MyContextHolder.myContextHolder.getNow().origins()
                    .fromName(data.getStringExtra(IntentExtra.ORIGIN_NAME.key))
            if (origin.isPersistent()) {
                onOriginSelected(origin)
            }
        }
        if (!origin.isPersistent()) {
            closeAndGoBack()
        }
    }

    private fun onOriginSelected(origin: Origin) {
        if (myAccount.origin == origin) return

        // If we have changed the System, we should recreate the Account
        state.builder.setOrigin(origin)
        showFragment(AccountSettingsFragment::class.java, FragmentAction.ON_ORIGIN_SELECTED.toBundle(Bundle()))
    }

    fun goToAddAccount() {
        if (state.getAccountAction() == Intent.ACTION_INSERT && isInvisibleView(R.id.uniqueName)
                && isInvisibleView(R.id.password)
                && isVisibleView(R.id.add_account)) {
            val addAccount = findFragmentViewById(R.id.add_account)
            addAccount?.performClick()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.account_settings, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.remove_account_menu_id)?.apply {
            val canRemove = isMaPersistent()
            isEnabled = canRemove
            isVisible = canRemove
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                closeAndGoBack()
                return true
            }
            R.id.preferences_menu_id -> startMyPreferenceActivity()
            R.id.remove_account_menu_id -> DialogFactory.showOkCancelDialog(
                    supportFragmentManager.findFragmentById(R.id.fragmentOne),
                    R.string.remove_account_dialog_title,
                    R.string.remove_account_dialog_text,
                    ActivityRequestCode.REMOVE_ACCOUNT)
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startMyPreferenceActivity() {
        startActivity(Intent(this, MySettingsActivity::class.java))
        finish()
    }

    fun updateScreen() {
        if (supportFragmentManager.findFragmentById(R.id.fragmentOne) == null) {
            MyLog.v(this, "No fragment found")
            return
        }
        showTitle()
        showErrors()
        showOrigin()
        showUniqueName()
        showPassword()
        showAccountState()
        showAddAccountButton()
        ViewUtils.showView(this, R.id.below_add_account, isMaPersistent())
        showHomeTimelineButton()
        showVerifyCredentialsButton()
        showLogOutButton()
        showIsDefaultAccount()
        showIsSyncedAutomatically()
        showSyncFrequency()
        showLastSyncSucceededDate()
    }

    fun showTitle() {
        val ma = myAccount
        if (ma.isValid || state.getAccountAction() != Intent.ACTION_INSERT) {
            var title = getText(R.string.account_settings_activity_title).toString()
            title += " - " + ma.getAccountName()
            setTitle(title)
        } else {
            setTitle(getText(R.string.header_add_new_account).toString())
        }
    }

    fun showErrors() {
        showTextView(R.id.latest_error_label, R.string.latest_error_label,
                mLatestErrorMessage.length > 0)
        showTextView(R.id.latest_error, mLatestErrorMessage, mLatestErrorMessage.length > 0)
    }

    private fun showOrigin() {
        val view = findFragmentViewById(R.id.origin_name) as TextView?
        if (view != null) {
            view.text = getText(R.string.title_preference_origin_system)
                    .toString().replace("{0}", state.builder.getOrigin().name)
                    .replace("{1}", state.builder.getOrigin().originType.title)
        }
    }

    private fun showUniqueName() {
        val origin = state.builder.getOrigin()
        showTextView(R.id.uniqueName_label, if (origin.hasHost()) R.string.title_preference_username else R.string.username_at_your_server,
                isMaPersistent() || state.isUsernameNeededToStartAddingNewAccount() == true)
        val nameEditable = findFragmentViewById(R.id.uniqueName) as EditText?
        if (nameEditable != null) {
            if (isMaPersistent() || state.isUsernameNeededToStartAddingNewAccount() == false) {
                nameEditable.visibility = View.GONE
            } else {
                nameEditable.visibility = View.VISIBLE
                nameEditable.hint = StringUtil.format(this,
                        if (origin.hasHost()) R.string.summary_preference_username else R.string.summary_preference_username_webfinger_id,
                        origin.name,
                        if (origin.hasHost()) SIMPLE_USERNAME_EXAMPLES else origin.originType.uniqueNameExamples
                )
                nameEditable.addTextChangedListener(textWatcher)
                if (nameEditable.text.isEmpty()) {
                    nameEditable.requestFocus()
                }
            }
            val nameShown: String = if (StringUtil.nonEmptyNonTemp(myAccount.username)) {
                if (origin.hasHost()) myAccount.username
                else myAccount.actor.uniqueName
            } else ""
            if (nameShown.compareTo(nameEditable.text.toString()) != 0) {
                nameEditable.setText(nameShown)
            }
            showTextView(R.id.uniqueName_readonly, nameShown, isMaPersistent())
        }
    }

    private fun isMaPersistent() = state.builder.isPersistent()

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // Empty
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // Empty
        }

        override fun afterTextChanged(s: Editable?) {
            clearError()
        }
    }

    private fun showPassword() {
        val ma = myAccount
        val isNeeded = state.builder.getConnection().isPasswordNeeded() && !ma.isValidAndSucceeded()
        val labelBuilder = StringBuilder()
        if (isNeeded) {
            labelBuilder.append(getText(R.string.summary_preference_password))
            if (ma.getPassword().isEmpty()) {
                labelBuilder.append(": (" + getText(R.string.not_set) + ")")
            }
        }
        showTextView(R.id.password_label, labelBuilder.toString(), isNeeded)
        val passwordEditable = findFragmentViewById(R.id.password) as EditText?
        if (passwordEditable != null) {
            if (ma.getPassword().compareTo(passwordEditable.text.toString()) != 0) {
                passwordEditable.setText(ma.getPassword())
            }
            passwordEditable.visibility = if (isNeeded) View.VISIBLE else View.GONE
            passwordEditable.isEnabled = !ma.isValidAndSucceeded()
            passwordEditable.addTextChangedListener(textWatcher)
        }
    }

    private fun showAccountState() {
        val ma = myAccount
        var summary: StringBuilder? = null
        if (isMaPersistent()) {
            summary = when (ma.getCredentialsVerified()) {
                CredentialsVerificationStatus.SUCCEEDED -> StringBuilder(
                        getText(R.string.summary_preference_verify_credentials))
                else -> if (isMaPersistent()) {
                    StringBuilder(
                            getText(R.string.summary_preference_verify_credentials_failed))
                } else {
                    if (state.builder.isOAuth()) {
                        StringBuilder(
                                getText(R.string.summary_preference_add_account_oauth))
                    } else {
                        StringBuilder(
                                getText(R.string.summary_preference_add_account_basic))
                    }
                }
            }
        }
        val stateText = findFragmentViewById(R.id.account_state) as TextView?
        if (stateText != null) {
            stateText.text = summary
        }
    }

    private val myAccount get() = state.getAccount()

    private fun showAddAccountButton() {
        val textView = showTextView(R.id.add_account, null, !isMaPersistent())
        if (textView != null && isVisibleView(R.id.add_account)) {
            textView.setOnClickListener(View.OnClickListener { v: View? -> onAddAccountClick(v) })
        }
    }

    fun onAddAccountClick(v: View?) {
        clearError()
        updateChangedFields()
        updateScreen()
        var error: CharSequence = ""
        var addAccountEnabled = !state.isUsernameNeededToStartAddingNewAccount() || myAccount.isUsernameValid()
        if (addAccountEnabled) {
            if (!state.builder.isOAuth() && state.builder.getPassword().isEmpty()) {
                addAccountEnabled = false
                error = getText(R.string.title_preference_password)
            }
        } else {
            error = getText(state.builder.getOrigin().alternativeTermForResourceId(R.string.title_preference_username))
        }
        if (addAccountEnabled) {
            verifyCredentials(true)
        } else {
            appendError(getText(R.string.error_invalid_value).toString() + ": " + error)
        }
    }

    fun clearError() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookie()
        if (mLatestErrorMessage.length > 0) {
            mLatestErrorMessage.setLength(0)
            showErrors()
        }
    }

    private fun showHomeTimelineButton() {
        val textView = showTextView(
                R.id.home_timeline, R.string.options_menu_home_timeline, isMaPersistent())
        textView?.setOnClickListener { v: View? ->
            updateChangedFields()
            activityOnFinish = ActivityOnFinish.HOME
            finish()
        }
    }

    private fun showVerifyCredentialsButton() {
        val buttonText: CharSequence = if (myAccount.getCredentialsPresent()) {
            if (myAccount.isValidAndSucceeded()) getText(R.string.title_preference_verify_credentials)
            else getText(R.string.title_preference_verify_credentials_failed)
        } else {
            String.format(getText(R.string.log_in_to_social_network).toString(),
                    state.builder.getOrigin().name)
        }
        showTextView(
                R.id.verify_credentials,
                buttonText,
                isMaPersistent())
                ?.setOnClickListener { v: View? ->
                    clearError()
                    updateChangedFields()
                    updateScreen()
                    verifyCredentials(true)
                }
    }

    private fun showLogOutButton() {
        showTextView(R.id.log_out,
                R.string.log_out,
                myAccount.getCredentialsPresent() ||
                        myAccount.connection.areOAuthClientKeysPresent() &&
                        myAccount.connection.http.data.oauthClientKeys?.areDynamic == true)
                ?.setOnClickListener {
                    with(state.builder) {
                        setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER)
                        getAccount().connection.clearClientKeys()
                        save()
                        MyPreferences.onPreferencesChanged()
                        closeAndGoBack()
                    }
                }
    }

    private fun showIsDefaultAccount() {
        val isDefaultAccount = myAccount == state.builder.myContext().accounts().getDefaultAccount()
        val view = findFragmentViewById(R.id.is_default_account)
        if (view != null) {
            view.visibility = if (isDefaultAccount) View.VISIBLE else View.GONE
        }
    }

    private fun showIsSyncedAutomatically() {
        MyCheckBox.set(findFragmentViewById(R.id.synced_automatically),
                state.builder.getAccount().isSyncedAutomatically()
        ) { buttonView: CompoundButton?, isChecked: Boolean -> state.builder.setSyncedAutomatically(isChecked) }
    }

    private fun showSyncFrequency() {
        val label = findFragmentViewById(R.id.label_sync_frequency) as TextView?
        val view = findFragmentViewById(R.id.sync_frequency) as EditText?
        if (label != null && view != null) {
            val labelText = getText(R.string.sync_frequency_minutes).toString() + " " +
                    SharedPreferencesUtil.getSummaryForListPreference(this, java.lang.Long.toString(MyPreferences.getSyncFrequencySeconds()),
                            R.array.fetch_frequency_values, R.array.fetch_frequency_entries,
                            R.string.summary_preference_frequency)
            label.text = labelText
            val value = if (state.builder.getAccount().getSyncFrequencySeconds() <= 0) ""
            else (state.builder.getAccount().getSyncFrequencySeconds() / 60).toString()
            view.setText(value)
            view.hint = labelText
            view.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // Empty
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Empty
                }

                override fun afterTextChanged(s: Editable?) {
                    val seconds = s.toString().let { StringUtil.toLong(it) * 60 }.takeIf { it > 0 } ?: 0
                    state.builder.setSyncFrequencySeconds(seconds)
                }
            })
        }
    }

    private fun showLastSyncSucceededDate() {
        val lastSyncSucceededDate = myAccount.getLastSyncSucceededDate()
        MyUrlSpan.showText(findFragmentViewById(R.id.last_synced) as TextView?,
                if (lastSyncSucceededDate == 0L) getText(R.string.never).toString()
                else RelativeTime.getDifference(this, lastSyncSucceededDate),
                TextMediaType.UNKNOWN, false, false)
    }

    private fun showTextView(textViewId: Int, textResourceId: Int, isVisible: Boolean): TextView? =
            showTextView(textViewId, if (textResourceId == 0) null else getText(textResourceId), isVisible)

    fun showTextView(textViewId: Int, text: CharSequence?, isVisible: Boolean): TextView? =
            (findFragmentViewById(textViewId) as TextView?)?.also {
                if (!TextUtils.isEmpty(text)) {
                    it.text = text
                }
                it.visibility = if (isVisible) View.VISIBLE else View.GONE
            }

    override fun onResume() {
        super.onResume()
        MyContextHolder.myContextHolder.getNow().setInForeground(true)
        if (restartMeIfNeeded()) return
        MyServiceManager.setServiceUnavailable()
        MyServiceManager.stopService()
        updateScreen()
        val uri = intent.data
        if (uri != null) {
            if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
                MyLog.d(this, "uri=$uri")
            }
            if (HttpConnectionInterface.CALLBACK_URI.getScheme() == uri.scheme) {
                // To prevent repeating of this task
                intent.data = null
                // This activity was started by Twitter ("Service Provider")
                // so start second step of OAuth Authentication process
                AsyncTaskLauncher<Uri?>().execute(this, OAuthAcquireAccessTokenTask(), uri)
                activityOnFinish = ActivityOnFinish.OUR_DEFAULT_SCREEN
            }
        }
        resumedOnce = true
    }

    /**
     * Verify credentials
     *
     * @param reVerify true - Verify only if we didn't do this yet
     */
    fun verifyCredentials(reVerify: Boolean) {
        val ma = myAccount
        if (reVerify || ma.getCredentialsVerified() == CredentialsVerificationStatus.NEVER) {
            MyServiceManager.setServiceUnavailable()
            val state2: MyServiceState = MyServiceManager.getServiceState()
            if (state2 != MyServiceState.STOPPED) {
                MyServiceManager.stopService()
                if (state2 != MyServiceState.UNKNOWN) {
                    appendError(getText(R.string.system_is_busy_try_later).toString() + " (" + state2 + ")")
                    return
                }
            }
            if (ma.getCredentialsPresent()) {
                // Credentials are present, so we may verify them
                // This is needed even for OAuth - to know Username
                AsyncTaskLauncher.execute(this,
                        VerifyCredentialsTask(ma.actor.getEndpoint(ActorEndpointType.API_PROFILE)))
                        .onFailure { e: Throwable -> appendError(e.message) }
            } else if (state.builder.isOAuth() && reVerify) {
                // Credentials are not present,
                // so start asynchronous OAuth Authentication process
                if (!ma.areClientKeysPresent()) {
                    AsyncTaskLauncher.execute(this, OAuthRegisterClientTask())
                            .onFailure { e: Throwable -> appendError(e.message) }
                } else {
                    AsyncTaskLauncher.execute(this, OAuthAcquireRequestTokenTask(this))
                            .onFailure { e: Throwable -> appendError(e.message) }
                    activityOnFinish = ActivityOnFinish.OUR_DEFAULT_SCREEN
                }
            }
        }
    }

    private fun updateChangedFields() {
        if (!isMaPersistent()) {
            val nameEditable = findFragmentViewById(R.id.uniqueName) as EditText?
            if (nameEditable != null) {
                val uniqueName = nameEditable.text.toString()
                if (uniqueName.compareTo(myAccount.getOAccountName().getUniqueName()) != 0) {
                    state.builder.setUniqueName(uniqueName)
                }
            }
        }
        val passwordEditable = findFragmentViewById(R.id.password) as EditText?
        if (passwordEditable != null
                && myAccount.getPassword().compareTo(passwordEditable.text.toString()) != 0) {
            state.builder.setPassword(passwordEditable.text.toString())
        }
    }

    fun appendError(errorMessage: CharSequence?) {
        if (TextUtils.isEmpty(errorMessage)) {
            return
        }
        if (mLatestErrorMessage.length > 0) {
            mLatestErrorMessage.append("/n")
        }
        mLatestErrorMessage.append(errorMessage)
        showErrors()
    }

    override fun onPause() {
        super.onPause()
        state.save()
        MyContextHolder.myContextHolder.getNow().setInForeground(false)
    }

    override fun finish() {
        if (resumedOnce || !isMyResumed()) {
            if (activityOnFinish == ActivityOnFinish.NONE) {
                MyContextHolder.myContextHolder.initialize(this)
            } else {
                returnToOurActivity()
            }
        }
        super.finish()
    }

    private fun returnToOurActivity() {
        MyContextHolder.myContextHolder
                .initialize(this)
                .whenSuccessAsync({ myContext: MyContext ->
                    MyLog.v(this, "Returning to $activityOnFinish")
                    val myAccount = myContext.accounts().fromAccountName(getState().getAccount().getAccountName())
                    if (myAccount.isValid) {
                        myContext.accounts().setCurrentAccount(myAccount)
                    }
                    if (activityOnFinish == ActivityOnFinish.HOME) {
                        val home = myContext.timelines()[TimelineType.HOME, myAccount.actor, Origin.EMPTY]
                        TimelineActivity.startForTimeline(myContext, this@AccountSettingsActivity, home, true,
                                initialSyncNeeded)
                        state.forget()
                    } else {
                        if (myContext.accounts().size() > 1) {
                            val intent = Intent(myContext.context(), MySettingsActivity::class.java)
                            // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                        } else {
                            FirstActivity.goHome(this@AccountSettingsActivity)
                        }
                    }
                }, AsyncTask.THREAD_POOL_EXECUTOR)
    }

    /**
     * This semaphore helps to avoid ripple effect: changes in MyAccount cause
     * changes in this activity ...
     */
    private var somethingIsBeingProcessed = false
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            closeAndGoBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Mark the action completed, close this activity and go back to the proper screen.
     * Return result to the caller if necessary.
     * See also com.android.email.activity.setup.AccountSetupBasics.finish() ...
     *
     * @return
     */
    private fun closeAndGoBack() {
        val message = saveState()
        if (!isFinishing) {
            MyLog.v(this) { "Go back: $message" }
            MySettingsActivity.goToMySettingsAccounts(this)
        }
    }

    private fun saveState(): String {
        if (state.isEmpty) return "(no state)"

        var message = "action=" + state.getAccountAction()
        // Explicitly save MyAccount only on "Back key"
        state.builder.save()
        state.actionCompleted = true
        activityOnFinish = ActivityOnFinish.OUR_DEFAULT_SCREEN
        if (state.authenticatorResponse != null) {
            // We should return result back to AccountManager
            activityOnFinish = ActivityOnFinish.NONE
            if (state.actionSucceeded) {
                if (isMaPersistent()) {
                    // Pass the new/edited account back to the account manager
                    val result = Bundle()
                    result.putString(AccountManager.KEY_ACCOUNT_NAME, state.getAccount().getAccountName())
                    result.putString(AccountManager.KEY_ACCOUNT_TYPE,
                            AuthenticatorService.ANDROID_ACCOUNT_TYPE)
                    state.authenticatorResponse?.onResult(result)
                    message += "; authenticatorResponse; account.name=" + state.getAccount().getAccountName() + "; "
                }
            } else {
                state.authenticatorResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "canceled")
            }
        }
        // Forget old state
        state.forget()
        return message
    }

    fun getState(): StateOfAccountChangeProcess {
        return state
    }

    /**
     * Step 1 of 3 of the OAuth Authentication
     * Needed in a case we don't have the AndStatus Client keys for this Microblogging system
     */
    private inner class OAuthRegisterClientTask() : MyAsyncTask<Void?, Void?, TaskResult?>("OAuthRegisterClientTask", PoolEnum.QUICK_UI) {
        private var dlg: ProgressDialog? = null
        override fun onPreExecute() {
            dlg = ProgressDialog.show(this@AccountSettingsActivity,
                    getText(R.string.dialog_title_registering_client),
                    getText(R.string.dialog_summary_registering_client),  // duration indeterminate
                    true,  // not cancelable
                    false)
        }

        override fun doInBackground2(params: Void?): TaskResult {
            var succeeded = false
            var connectionErrorMessage = ""
            try {
                if (!myAccount.areClientKeysPresent()) {
                    state.builder.registerClient()
                }
                if (myAccount.areClientKeysPresent()) {
                    state.builder.getOriginConfig()
                    succeeded = true
                }
            } catch (e: ConnectionException) {
                connectionErrorMessage = e.message ?: ""
                MyLog.i(this, e)
            }
            var stepErrorMessage = ""
            if (!succeeded) {
                stepErrorMessage = this@AccountSettingsActivity
                        .getString(R.string.client_registration_failed)
                if (!connectionErrorMessage.isEmpty()) {
                    stepErrorMessage += ": $connectionErrorMessage"
                }
                MyLog.d(this, stepErrorMessage)
            }
            return TaskResult(if (succeeded) ResultStatus.SUCCESS else ResultStatus.NONE, stepErrorMessage)
        }

        // This is in the UI thread, so we can mess with the UI
        override fun onPostExecute2(result: TaskResult?) {
            DialogFactory.dismissSafely(dlg)
            if (result != null && !this@AccountSettingsActivity.isFinishing) {
                if (result.isSuccess()) {
                    state.builder.myContext().setExpired { "Client registered" }
                    MyContextHolder.myContextHolder
                            .initialize(this@AccountSettingsActivity, this)
                            .whenSuccessAsync({ myContext: MyContext ->
                                state.builder.rebuildMyAccount(myContext)
                                updateScreen()
                                AsyncTaskLauncher.execute(this,
                                        OAuthAcquireRequestTokenTask(this@AccountSettingsActivity))
                                        .onFailure({ e: Throwable -> appendError(e.message) })
                                activityOnFinish = ActivityOnFinish.OUR_DEFAULT_SCREEN
                            }, UiThreadExecutor.INSTANCE)
                } else {
                    appendError(result.message)
                    state.builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED)
                    updateScreen()
                }
            }
            MyLog.v(this, I18n.succeededText(result != null && result.isSuccess()))
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
     * in order to Obtain User Authorization.
     * 4. This task ends.
     *
     * What will occur later:
     * 5. After User Authorized AndStatus in the Internet Browser,
     * Twitter site will redirect User back to
     * AndStatus and then the second OAuth task will start.
     *
     * @author yvolk@yurivolkov.com This code is based on "BLOA" example,
     * http://github.com/brione/Brion-Learns-OAuth yvolk: I had to move
     * this code from OAuthActivity here in order to be able to show
     * ProgressDialog and to get rid of any "Black blank screens"
     */
    private class OAuthAcquireRequestTokenTask(private val activity: AccountSettingsActivity) : MyAsyncTask<Void?, Void?, TaskResult?>(PoolEnum.QUICK_UI) {
        private var dlg: ProgressDialog? = null
        override fun onPreExecute() {
            dlg = ProgressDialog.show(activity,
                    activity.getText(R.string.dialog_title_acquiring_a_request_token),
                    activity.getText(R.string.dialog_summary_acquiring_a_request_token),  // indeterminate duration
                    true,  // not cancelable
                    false
            )
        }

        override fun doInBackground2(params: Void?): TaskResult {
            var stepErrorMessage = ""
            var connectionErrorMessage = ""
            var authUri = Uri.EMPTY
            try {
                val connection = activity.state.builder.getConnection()
                MyLog.v(this, "Retrieving request token for $connection")
                val oAuthService = connection.getOAuthService()
                if (oAuthService == null) {
                    connectionErrorMessage = "No OAuth service for $connection"
                } else if (!connection.areOAuthClientKeysPresent()) {
                    connectionErrorMessage = "No Client keys for $connection"
                } else {
                    if (oAuthService.isOAuth2()) {
                        oAuthService.getService(true)?.let { service ->
                            authUri = UriUtils.fromString(service.authorizationUrl)
                        }
                    } else {
                        val consumer = oAuthService.getConsumer()

                        // This is really important. If you were able to register your
                        // real callback Uri with Twitter, and not some fake Uri
                        // like I registered when I wrote this example, you need to send
                        // null as the callback Uri in this function call. Then
                        // Twitter will correctly process your callback redirection
                        authUri = UriUtils.fromString(oAuthService.getProvider()
                                ?.retrieveRequestToken(consumer, HttpConnectionInterface.CALLBACK_URI.toString()))
                        activity.state.setRequestTokenWithSecret(consumer?.token, consumer?.tokenSecret)
                    }

                    // This is needed in order to complete the process after redirect
                    // from the Browser to the same activity.
                    activity.state.actionCompleted = false
                }
            } catch (e: OAuthMessageSignerException) {
                connectionErrorMessage = e.message ?: ""
                MyLog.i(this, e)
                authUri = Uri.EMPTY
            } catch (e: OAuthNotAuthorizedException) {
                connectionErrorMessage = e.message ?: ""
                MyLog.i(this, e)
                authUri = Uri.EMPTY
            } catch (e: OAuthExpectationFailedException) {
                connectionErrorMessage = e.message ?: ""
                MyLog.i(this, e)
                authUri = Uri.EMPTY
            } catch (e: OAuthCommunicationException) {
                connectionErrorMessage = e.message ?: ""
                MyLog.i(this, e)
                authUri = Uri.EMPTY
            } catch (e: ConnectionException) {
                connectionErrorMessage = e.message ?: ""
                MyLog.i(this, e)
                authUri = Uri.EMPTY
            }
            val resultStatus = if (UriUtils.isDownloadable(authUri)) ResultStatus.SUCCESS else ResultStatus.CONNECTION_EXCEPTION
            if (resultStatus != ResultStatus.SUCCESS) {
                stepErrorMessage = activity.getString(R.string.acquiring_a_request_token_failed)
                if (!connectionErrorMessage.isEmpty()) {
                    stepErrorMessage += ": $connectionErrorMessage"
                }
                MyLog.d(this, stepErrorMessage)
                activity.state.builder.clearClientKeys()
            }
            return TaskResult.withAuthUri(resultStatus, stepErrorMessage, authUri)
        }

        // This is in the UI thread, so we can mess with the UI
        override fun onPostExecute2(result: TaskResult?) {
            DialogFactory.dismissSafely(dlg)
            if (result != null && !activity.isFinishing()) {
                if (result.isSuccess()) {
                    activity.activityOnFinish = ActivityOnFinish.NONE
                    MyLog.d(activity, "Starting Web view at " + result.authUri)
                    val i = Intent(activity, AccountSettingsWebActivity::class.java)
                    i.putExtra(AccountSettingsWebActivity.EXTRA_URLTOOPEN, result.authUri.toString())
                    activity.startActivity(i)

                    // Finish this activity in order to start properly
                    // after redirection from Browser
                    // Because of initializations in onCreate...
                    activity.finish()
                } else {
                    activity.appendError(result.message)
                    activity.state.builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED)
                    activity.updateScreen()
                }
            }
            MyLog.v(this, I18n.succeededText(result != null && result.isSuccess()))
        }
    }

    /**
     * Task 3 of 3 required for OAuth Authentication.
     *
     * During this task:
     * 1. AndStatus ("Consumer") exchanges "Request Token",
     * obtained earlier from Twitter ("Service provider"),
     * for "Access Token".
     * 2. Stores the Access token for all future interactions with Twitter.
     *
     * @author yvolk@yurivolkov.com This code is based on "BLOA" example,
     * http://github.com/brione/Brion-Learns-OAuth yvolk: I had to move
     * this code from OAuthActivity here in order to be able to show
     * ProgressDialog and to get rid of any "Black blank screens"
     */
    private inner class OAuthAcquireAccessTokenTask() : MyAsyncTask<Uri?, Void?, TaskResult?>(PoolEnum.QUICK_UI) {
        private var dlg: ProgressDialog? = null
        override fun onPreExecute() {
            dlg = ProgressDialog.show(this@AccountSettingsActivity,
                    getText(R.string.dialog_title_acquiring_an_access_token),
                    getText(R.string.dialog_summary_acquiring_an_access_token),  // indeterminate duration
                    true,  // not cancelable
                    false
            )
        }

        override fun doInBackground2(params: Uri?): TaskResult {
            var message = ""
            var accessToken = ""
            var accessSecret = ""
            var whoAmI: Optional<Uri> = Optional.empty()
            if (myAccount.getOAuthService() == null) {
                message = "Connection is not OAuth"
                MyLog.e(this, message)
            } else {
                // We don't need to worry about any saved states: we can reconstruct the state
                if (params != null && HttpConnectionInterface.CALLBACK_URI.getHost() != null &&
                        HttpConnectionInterface.CALLBACK_URI.getHost() == params.host) {
                    state.builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER)
                    try {
                        if (myAccount.getOAuthService()?.isOAuth2() == true) {
                            val authCode = params.getQueryParameter("code")
                            MyLog.d(this, "Auth code is: $authCode")
                            val service = myAccount.getOAuthService()?.getService(true)
                            val token = service?.getAccessToken(authCode)
                            accessToken = token?.accessToken ?: ""
                            accessSecret = token?.rawResponse ?: ""
                            whoAmI = MyOAuth2AccessTokenJsonExtractor.extractWhoAmI(accessSecret)
                        } else {
                            val requestToken = state.getRequestToken()
                            val requestSecret = state.getRequestSecret()
                            // Clear the request stuff, we've used it already
                            state.setRequestTokenWithSecret(null, null)
                            val consumer = myAccount.getOAuthService()?.getConsumer()
                            if (!(requestToken == null || requestSecret == null)) {
                                consumer?.setTokenWithSecret(requestToken, requestSecret)
                            }
                            val oauthToken = params.getQueryParameter(OAuth.OAUTH_TOKEN)
                            val verifier = params.getQueryParameter(OAuth.OAUTH_VERIFIER)

                            /*
                             * yvolk 2010-07-08: It appeared that this may be not true:
                             * Assert.assertEquals(otoken, mConsumer.getToken()); (e.g.
                             * if User denied access during OAuth...) hence this is not
                             * Assert :-)
                             */if (oauthToken != null || consumer?.token != null) {
                                myAccount.getOAuthService()?.getProvider()
                                        ?.retrieveAccessToken(consumer, verifier)
                                // Now we can retrieve the goodies
                                accessToken = consumer?.token ?: ""
                                accessSecret = consumer?.tokenSecret ?: ""
                            }
                        }
                    } catch (e: Exception) {
                        message = e.message ?: ""
                        MyLog.i(this, e)
                    } finally {
                        state.builder.setUserTokenWithSecret(accessToken, accessSecret)
                        MyLog.d(this, "Access token for " + myAccount.getAccountName() +
                                ": " + accessToken + ", " + accessSecret)
                    }
                }
            }
            return TaskResult.withWhoAmI(
                    if (!accessToken.isEmpty() && !accessSecret.isEmpty()) ResultStatus.SUCCESS else ResultStatus.CREDENTIALS_OF_OTHER_ACCOUNT,
                    message,
                    whoAmI
            )
        }

        // This is in the UI thread, so we can mess with the UI
        override fun onPostExecute2(result: TaskResult?) {
            DialogFactory.dismissSafely(dlg)
            if (result != null && !this@AccountSettingsActivity.isFinishing) {
                if (result.isSuccess()) {
                    // Credentials are present, so we may verify them
                    // This is needed even for OAuth - to know Twitter Username
                    AsyncTaskLauncher.execute(this, VerifyCredentialsTask(result.whoAmI))
                            .onFailure { e: Throwable -> appendError(e.message) }
                } else {
                    val stepErrorMessage = this@AccountSettingsActivity
                            .getString(R.string.acquiring_an_access_token_failed) +
                            if (!result.message.isNullOrEmpty()) ": " + result.message else ""
                    appendError(stepErrorMessage)
                    state.builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED)
                    updateScreen()
                }
            }
            MyLog.v(this, I18n.succeededText(result != null && result.isSuccess()))
        }
    }

    /**
     * Assuming we already have credentials to verify, verify them
     * @author yvolk@yurivolkov.com
     */
    private inner class VerifyCredentialsTask(private val whoAmI: Optional<Uri>) : MyAsyncTask<Void?, Void?, TaskResult?>(PoolEnum.QUICK_UI) {
        private var dlg: ProgressDialog? = null

        @Volatile
        private var skip = false
        override fun onPreExecute() {
            activityOnFinish = ActivityOnFinish.NONE
            dlg = ProgressDialog.show(this@AccountSettingsActivity,
                    getText(R.string.dialog_title_checking_credentials),
                    getText(R.string.dialog_summary_checking_credentials),  // indeterminate duration
                    true,  // not cancelable
                    false
            )
            synchronized(this@AccountSettingsActivity) {
                if (somethingIsBeingProcessed) {
                    skip = true
                } else {
                    somethingIsBeingProcessed = true
                }
            }
        }

        override fun doInBackground2(params: Void?): TaskResult? {
            return if (skip) TaskResult(ResultStatus.NONE) else Try.success(state.builder)
                    .flatMap { it?.getOriginConfig() }
                    .flatMap { b: MyAccount.Builder -> b.getConnection().verifyCredentials(whoAmI) }
                    .flatMap { actor: Actor -> state.builder.onCredentialsVerified(actor) }
                    .map({ it.getAccount() })
                    .filter { obj: MyAccount -> obj.isValidAndSucceeded() }
                    .onSuccess { myAccount: MyAccount ->
                        state.forget()
                        val myContext: MyContext = MyContextHolder.myContextHolder.initialize(this@AccountSettingsActivity, this).getBlocking()
                        FirstActivity.checkAndUpdateLastOpenedAppVersion(this@AccountSettingsActivity, true)
                        val timeline = myContext.timelines().forUser(TimelineType.HOME, myAccount.actor)
                        if (timeline.isTimeToAutoSync()) {
                            initialSyncNeeded = true
                            activityOnFinish = ActivityOnFinish.HOME
                        }
                    }
                    .map { ma: MyAccount -> TaskResult(ResultStatus.SUCCESS, "") }
                    .recover(ConnectionException::class.java) { e: ConnectionException ->
                        val status: ResultStatus = when (e.getStatusCode()) {
                            StatusCode.AUTHENTICATION_ERROR -> ResultStatus.ACCOUNT_INVALID
                            StatusCode.CREDENTIALS_OF_OTHER_ACCOUNT -> ResultStatus.CREDENTIALS_OF_OTHER_ACCOUNT
                            else -> ResultStatus.CONNECTION_EXCEPTION
                        }
                        MyLog.v(this, e)
                        TaskResult(status, e.toString())
                    }
                    .recover(NonFatalException::class.java) { e: NonFatalException ->
                        TaskResult(ResultStatus.CONNECTION_EXCEPTION, e.message)
                    }
                    .get()
        }

        /**
         * Credentials were verified just now!
         */
        override fun onPostExecute2(result: TaskResult?) {
            DialogFactory.dismissSafely(dlg)
            if (this@AccountSettingsActivity.isFinishing) return
            val resultOut = result ?: TaskResult(ResultStatus.NONE)
            var errorMessage: CharSequence = ""
            when (resultOut.status) {
                ResultStatus.SUCCESS -> Toast.makeText(this@AccountSettingsActivity, R.string.authentication_successful,
                        Toast.LENGTH_SHORT).show()
                ResultStatus.ACCOUNT_INVALID -> errorMessage = getText(R.string.dialog_summary_authentication_failed)
                ResultStatus.CREDENTIALS_OF_OTHER_ACCOUNT -> errorMessage = getText(R.string.error_credentials_of_other_user)
                ResultStatus.CONNECTION_EXCEPTION -> {
                    errorMessage = "${getText(R.string.error_connection_error)} ${resultOut.message}"
                    MyLog.i(this, errorMessage.toString())
                }
                else -> {
                }
            }
            if (activityOnFinish == ActivityOnFinish.HOME) {
                finish()
                return
            }
            if (!skip) {
                // Note: MyAccount was already saved inside MyAccount.verifyCredentials
                // Now we only have to deal with the state
                state.actionSucceeded = resultOut.isSuccess()
                if (resultOut.isSuccess()) {
                    state.actionCompleted = true
                    if (state.getAccountAction()?.compareTo(Intent.ACTION_INSERT) == 0) {
                        state.setAccountAction(Intent.ACTION_EDIT)
                    }
                }
                somethingIsBeingProcessed = false
            }
            updateScreen()
            appendError(errorMessage)
        }

        init {
            setCancelable(false) // This is needed because there is initialize in the background!
        }
    }

    override fun classTag(): String {
        return TAG
    }

    companion object {
        private val TAG: String = AccountSettingsActivity::class.java.simpleName
        fun startAddNewAccount(context: Context, originName: String?, clearTask: Boolean) {
            val intent: Intent
            intent = Intent(context, AccountSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK +
                    if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0
            )
            intent.action = Intent.ACTION_INSERT
            if (!originName.isNullOrEmpty()) {
                intent.putExtra(IntentExtra.ORIGIN_NAME.key, originName)
            }
            MyLog.i(TAG, "startAddNewAccount with $intent")
            context.startActivity(intent)
        }
    }
}