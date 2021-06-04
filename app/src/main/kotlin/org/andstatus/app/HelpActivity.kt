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
package org.andstatus.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import org.andstatus.app.account.AccountSettingsActivity
import org.andstatus.app.backup.DefaultProgressListener
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.backup.RestoreActivity
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.ExecutionMode
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyContextState
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.Permissions
import org.andstatus.app.util.Permissions.PermissionType
import org.andstatus.app.util.ViewUtils
import org.andstatus.app.widget.WebViewFragment

class HelpActivity : MyActivity() {
    private var helpFlipper: ViewPager? = null

    /** Stores state of [.EXTRA_IS_FIRST_ACTIVITY]  */
    private var mIsFirstActivity = false
    private var wasPaused = false

    @Volatile
    private var progressListener: ProgressLogger.ProgressListener = ProgressLogger.EMPTY_LISTENER
    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.help
        super.onCreate(savedInstanceState)
        if (isFinishing || isCloseRequest(intent)) {
            return
        }
        if (savedInstanceState != null) {
            mIsFirstActivity = savedInstanceState.getBoolean(EXTRA_IS_FIRST_ACTIVITY, false)
        }
        if (intent.hasExtra(EXTRA_IS_FIRST_ACTIVITY)) {
            mIsFirstActivity = intent.getBooleanExtra(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity)
        }
        if (MyContextHolder.myContextHolder.getNow().accounts().currentAccount.nonValid
            && MyContextHolder.myContextHolder.executionMode == ExecutionMode.ROBO_TEST && !generatingDemoData) {
            progressListener.cancel()
            generatingDemoData = true
            progressListener = DefaultProgressListener(this, R.string.app_name, "GenerateDemoData")
            DemoData.demoData.addAsync( MyContextHolder.myContextHolder.getNow(), progressListener)
        }
        showRestoreButton()
        showGetStartedButton()
        setupHelpFlipper()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        isCloseRequest(intent)
    }

    private fun isCloseRequest(intent: Intent): Boolean {
        if (isFinishing) {
            return true
        }
        if (intent.hasExtra(EXTRA_CLOSE_ME)) {
            finish()
            return true
        }
        return false
    }

    private fun showRestoreButton() {
        val restoreButton = findViewById<Button?>(R.id.button_restore)
        if (!generatingDemoData
                &&  MyContextHolder.myContextHolder.getNow().isReady() &&  MyContextHolder.myContextHolder.getNow().accounts().isEmpty) {
            restoreButton.setOnClickListener { v: View? ->
                startActivity(Intent(this@HelpActivity, RestoreActivity::class.java))
                finish()
            }
        } else {
            restoreButton.visibility = View.GONE
        }
    }

    private fun showGetStartedButton() {
        //The button is always visible in order to avoid a User's confusion,
        val getStarted = findViewById<Button?>(R.id.button_help_get_started)
        getStarted.visibility = if (generatingDemoData) View.GONE else View.VISIBLE
        getStarted.setOnClickListener { v: View? ->
            if ( MyContextHolder.myContextHolder.getFuture().isCompletedExceptionally()) {
                 MyContextHolder.myContextHolder.initialize(this).thenStartApp()
                return@setOnClickListener
            }
            when ( MyContextHolder.myContextHolder.getNow().state()) {
                MyContextState.READY -> {
                    FirstActivity.checkAndUpdateLastOpenedAppVersion(this@HelpActivity, true)
                    if ( MyContextHolder.myContextHolder.getNow().accounts().currentAccount.isValid) {
                        startActivity(Intent(this@HelpActivity, TimelineActivity::class.java))
                    } else {
                        startActivity(Intent(this@HelpActivity, AccountSettingsActivity::class.java))
                    }
                    finish()
                }
                MyContextState.NO_PERMISSIONS ->                     // Actually this is not used for now...
                    Permissions.checkPermissionAndRequestIt(this@HelpActivity,
                            PermissionType.GET_ACCOUNTS)
                MyContextState.UPGRADING -> DialogFactory.showOkAlertDialog(this@HelpActivity, this@HelpActivity,
                        R.string.app_name, R.string.label_upgrading)
                MyContextState.DATABASE_UNAVAILABLE -> DialogFactory.showOkAlertDialog(this@HelpActivity, this@HelpActivity,
                        R.string.app_name, R.string.database_unavailable_description)
                else -> DialogFactory.showOkAlertDialog(this@HelpActivity, this@HelpActivity,
                        R.string.app_name, R.string.loading)
            }
        }
        if ( MyContextHolder.myContextHolder.getNow().accounts().currentAccount.isValid) {
            getStarted.setText(R.string.button_skip)
        }
    }

    class LogoFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.splash, container, false)
            showVersionText(inflater.context, view)
            ViewUtils.showView(view, R.id.system_info_section, MyPreferences.isShowDebuggingInfoInUi()
                    || MyContextHolder.myContextHolder.executionMode != ExecutionMode.DEVICE)
            if (MyPreferences.isShowDebuggingInfoInUi()) {
                MyUrlSpan.showText(view, R.id.system_info,
                         MyContextHolder.myContextHolder.getSystemInfo(inflater.context, false),
                        false, false)
            }
            return view
        }

        private fun showVersionText(context: Context?, parentView: View) {
            val versionText = parentView.findViewById<TextView?>(R.id.splash_application_version)
            val text: MyStringBuilder = MyStringBuilder.of( MyContextHolder.myContextHolder.getVersionText(context))
            if (! MyContextHolder.myContextHolder.getNow().isReady()) {
                text.withSpace(MyContextHolder.myContextHolder.getNow().state().toString())
                text.withSpace(MyContextHolder.myContextHolder.getNow().getLastDatabaseError())
            }
             MyContextHolder.myContextHolder.tryNow().onFailure({ e: Throwable ->
                text.append(""" ${e.message} ${MyLog.getStackTrace(e)}""") })
            versionText.text = text.toString()
            versionText.setOnClickListener { v: View? ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("http://andstatus.org")
                startActivity(intent)
            }
        }
    }

    private fun setupHelpFlipper() {
        val flipper: ViewPager = findViewById(R.id.help_flipper)
        helpFlipper = flipper
        flipper.setAdapter(object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getCount(): Int {
                return 3
            }

            override fun getItem(position: Int): Fragment {
                return when (position) {
                    PAGE_USER_GUIDE -> WebViewFragment.from(R.raw.user_guide, R.raw.fb2_2_html)
                    PAGE_CHANGELOG -> WebViewFragment.from(R.raw.changes, R.raw.changes2html)
                    else -> LogoFragment()
                }
            }
        })
        if (ViewUtils.showView(this, R.id.button_help_learn_more,  MyContextHolder.myContextHolder.getNow().isReady())) {
            val learnMore = findViewById<Button?>(R.id.button_help_learn_more)
            learnMore.setOnClickListener { v: View? ->
                val adapter = flipper.getAdapter()
                if (adapter != null) {
                    flipper.setCurrentItem(
                            if (flipper.getCurrentItem() >= adapter.count - 1) 0 else flipper.getCurrentItem() + 1,
                            true)
                }
            }
        }
        if (intent.hasExtra(EXTRA_HELP_PAGE_INDEX)) {
            val pageToStart = intent.getIntExtra(EXTRA_HELP_PAGE_INDEX, 0)
            if (pageToStart > 0) {
                flipper.setCurrentItem(pageToStart, true)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (!generatingDemoData) {
            menuInflater.inflate(R.menu.help, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.preferences_menu_id -> startActivity(Intent(this, MySettingsActivity::class.java))
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity)
    }

    override fun onResume() {
        super.onResume()
         MyContextHolder.myContextHolder.upgradeIfNeeded(this)
        if (wasPaused && mIsFirstActivity
                &&  MyContextHolder.myContextHolder.getNow().accounts().currentAccount.isValid) {
            // We assume that user pressed back after adding first account
            val intent = Intent(this, TimelineActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        wasPaused = true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
         MyContextHolder.myContextHolder.getNow().setExpired { "onRequestPermissionsResult" }
        recreate()
    }

    override fun finish() {
        progressListener.onActivityFinish()
        generatingDemoData = false
        super.finish()
    }

    companion object {
        val TAG: String = HelpActivity::class.java.simpleName

        /**
         * integer - Index of Help screen to show first
         */
        val EXTRA_HELP_PAGE_INDEX: String = ClassInApplicationPackage.PACKAGE_NAME + ".HELP_PAGE_ID"

        /**
         * boolean - If the activity is the first then we should provide means
         * to start [TimelineActivity] from this activity
         */
        val EXTRA_IS_FIRST_ACTIVITY: String = ClassInApplicationPackage.PACKAGE_NAME + ".IS_FIRST_ACTIVITY"
        val EXTRA_CLOSE_ME: String = ClassInApplicationPackage.PACKAGE_NAME + ".CLOSE_ME"
        const val PAGE_CHANGELOG = 0
        const val PAGE_USER_GUIDE = 1
        const val PAGE_LOGO = 2

        @Volatile
        private var generatingDemoData = false

        fun startMe(context: Context, helpAsFirstActivity: Boolean, pageIndex: Int) {
            val intent = Intent(context, HelpActivity::class.java)
            if (helpAsFirstActivity) {
                intent.putExtra(EXTRA_IS_FIRST_ACTIVITY, true)
            }
            intent.putExtra(EXTRA_HELP_PAGE_INDEX, pageIndex)
            if (context is Activity) {
                context.startActivity(intent)
                MyLog.v(TAG) { "Finishing " + context.javaClass.simpleName + " and starting " + TAG }
                context.finish()
            } else {
                MyLog.v(TAG) { "Starting " + TAG + " from " + context.getApplicationContext().javaClass.name }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.getApplicationContext().startActivity(intent)
            }
        }
    }
}
