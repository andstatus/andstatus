/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.AndroidRuntimeException
import androidx.appcompat.app.AppCompatActivity
import io.vavr.control.Try
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MyContextState
import org.andstatus.app.context.MyLocale
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MySettingsGroup
import org.andstatus.app.data.DbUtils
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer

/** Activity to be started, when Application is not initialised yet (or needs re-initialization).
 * It allows to avoid "Application not responding" errors.
 * It is transparent and shows progress indicator only, launches next activity after application initialization.
 */
class FirstActivity() : AppCompatActivity(), Identifiable {
    override val instanceId: Long = InstanceId.next()

    enum class NeedToStart {
        HELP, CHANGELOG, OTHER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (isFirstrun.compareAndSet(true, false)) {
                MyLocale.onAttachBaseContext(this)
            }
            setContentView(R.layout.loading)
        } catch (e: Throwable) {
            MyLog.w(this, "Couldn't setContentView", e)
        }
        parseNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        parseNewIntent(intent)
    }

    private fun parseNewIntent(intent: Intent?) {
        when (MyAction.fromIntent(intent)) {
            MyAction.INITIALIZE_APP -> myContextHolder.initialize(this)
                .then("finishFirstActivity", true) { myContext: MyContext? -> finish() }
            MyAction.SET_DEFAULT_VALUES -> {
                setDefaultValuesOnUiThread(this)
                finish()
            }
            MyAction.CLOSE_ALL_ACTIVITIES -> finish()
            else -> {
                myContextHolder.initialize(this)
                    .thenTry("startNextActivity", true) { tryMyContext: Try<MyContext> ->
                        tryMyContext.flatMap { myContext ->
                            val msg = "Launching next activity from $instanceIdString"
                            if ((myContext.isReady || myContext.state == MyContextState.UPGRADING) && !myContext.isExpired) {
                                startNextActivitySync(myContext, intent)
                                TryUtils.SUCCESS
                            } else TryUtils.failure<Unit>("$msg, MyContext is not ready: ${myContext.state}")
                        }
                    }
                finish()
            }
        }
    }

    private val startNextActivity: BiConsumer<MyContext?, Throwable?> =
        BiConsumer { myContext: MyContext?, throwable: Throwable? ->
            var launched = false
            if (myContext != null && myContext.isReady && !myContext.isExpired) {
                try {
                    startNextActivitySync(myContext, intent)
                    launched = true
                } catch (e: AndroidRuntimeException) {
                    MyLog.w(instanceTag, "Launching next activity from firstActivity", e)
                } catch (e: SecurityException) {
                    MyLog.d(instanceTag, "Launching activity", e)
                }
            }
            if (!launched) {
                HelpActivity.startMe(
                    if (myContext == null) myContextHolder.getNow().context else myContext.context,
                    true, HelpActivity.PAGE_LOGO
                )
            }
            finish()
        }

    private fun startNextActivitySync(myContext: MyContext, myIntent: Intent?) {
        when (needToStartNext(this, myContext)) {
            NeedToStart.HELP -> HelpActivity.startMe(this, true, HelpActivity.PAGE_LOGO)
            NeedToStart.CHANGELOG -> HelpActivity.startMe(this, true, HelpActivity.PAGE_CHANGELOG)
            else -> {
                val intent = Intent(this, TimelineActivity::class.java)
                if (myIntent != null) {
                    val action = myIntent.action
                    if (!action.isNullOrEmpty()) {
                        intent.action = action
                    }
                    val data = myIntent.data
                    if (data != null) {
                        intent.data = data
                    }
                    val extras = myIntent.extras
                    if (extras != null) {
                        intent.putExtras(extras)
                    }
                }
                startActivity(intent)
            }
        }
    }

    companion object {
        private val SET_DEFAULT_VALUES: String = "setDefaultValues"
        private val resultOfSettingDefaults: AtomicReference<TriState> = AtomicReference(TriState.UNKNOWN)
        var isFirstrun: AtomicBoolean = AtomicBoolean(true)

        /**
         * Based on http://stackoverflow.com/questions/14001963/finish-all-activities-at-a-time
         */
        fun closeAllActivities(context: Context) {
            context.startActivity(
                MyAction.CLOSE_ALL_ACTIVITIES.newIntent()
                    .setClass(context, FirstActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun goHome(activity: MyActivity) {
            try {
                MyLog.v(activity.instanceTag) { "goHome" }
                startApp(activity)
            } catch (e: Exception) {
                MyLog.v(activity.instanceTag, "goHome", e)
                myContextHolder.thenStartApp("goHome")
            }
        }

        fun startApp(myContext: MyContext) {
            myContext.context.let { startApp(it) }
        }

        private fun startApp(context: Context) {
            MyLog.i(context, "startApp")
            val intent = Intent(context, FirstActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        fun needToStartNext(context: Context, myContext: MyContext): NeedToStart {
            if (!myContext.isReady) {
                MyLog.i(context, "Context is not ready: " + myContext.toString())
                return NeedToStart.HELP
            } else if (myContext.accounts.isEmpty) {
                MyLog.i(context, "No AndStatus Accounts yet")
                return NeedToStart.HELP
            }
            return if (myContext.isReady && checkAndUpdateLastOpenedAppVersion(context, false)) {
                NeedToStart.CHANGELOG
            } else NeedToStart.OTHER
        }

        /**
         * @return true if we opened previous version
         */
        fun checkAndUpdateLastOpenedAppVersion(context: Context, update: Boolean): Boolean {
            var changed = false
            val versionCodeLast = SharedPreferencesUtil.getLong(MyPreferences.KEY_VERSION_CODE_LAST)
            val pm = context.getPackageManager()
            val pi: PackageInfo?
            try {
                pi = pm.getPackageInfo(context.getPackageName(), 0)
                val versionCode = pi.versionCode
                if (versionCodeLast < versionCode) {
                    // Even if the Actor will see only the first page of the Help activity,
                    // count this as showing the Change Log
                    MyLog.v(
                        FirstActivity::class
                    ) {
                        ("Last opened version=" + versionCodeLast
                            + ", current is " + versionCode
                            + if (update) ", updating" else "")
                    }
                    changed = true
                    if (update && myContextHolder.getNow().isReady) {
                        SharedPreferencesUtil.putLong(MyPreferences.KEY_VERSION_CODE_LAST, versionCode.toLong())
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                MyLog.e(FirstActivity::class, "Unable to obtain package information", e)
            }
            return changed
        }

        fun startMeAsync(context: Context, myAction: MyAction) {
            val intent = Intent(context, FirstActivity::class.java)
            intent.action = myAction.action
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /** @return success
         */
        fun setDefaultValues(context: Context?): Boolean {
            if (context == null) {
                MyLog.e(FirstActivity::class, SET_DEFAULT_VALUES + " no context")
                return false
            }
            synchronized(resultOfSettingDefaults) {
                resultOfSettingDefaults.set(TriState.UNKNOWN)
                try {
                    if (context is Activity) {
                        context.runOnUiThread(Runnable { setDefaultValuesOnUiThread(context) })
                    } else {
                        startMeAsync(context, MyAction.SET_DEFAULT_VALUES)
                    }
                    for (i in 0..99) {
                        DbUtils.waitMs(FirstActivity::class, 50)
                        if (resultOfSettingDefaults.get().known) break
                    }
                } catch (e: Exception) {
                    MyLog.e(FirstActivity::class, "$SET_DEFAULT_VALUES error:${e.message} \n${MyLog.getStackTrace(e)}")
                }
            }
            return resultOfSettingDefaults.get().toBoolean(false)
        }

        private fun setDefaultValuesOnUiThread(activity: Activity) {
            try {
                MyLog.i(activity, SET_DEFAULT_VALUES + " started")
                MySettingsGroup.setDefaultValues(activity)
                resultOfSettingDefaults.set(TriState.TRUE)
                MyLog.i(activity, SET_DEFAULT_VALUES + " completed")
                return
            } catch (e: Exception) {
                MyLog.w(activity, "$SET_DEFAULT_VALUES error:${e.message} \n${MyLog.getStackTrace(e)}")
            }
            resultOfSettingDefaults.set(TriState.FALSE)
        }
    }
}
