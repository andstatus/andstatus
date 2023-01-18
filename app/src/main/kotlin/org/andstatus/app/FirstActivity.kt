/*
 * Copyright (C) 2023 yvolk (Yuri Volkov), http://yurivolkov.com
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
import androidx.appcompat.app.AppCompatActivity
import io.vavr.control.Try
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MyContextState
import org.andstatus.app.context.MyFutureContext
import org.andstatus.app.context.MyLocale
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MySettingsGroup
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import java.util.concurrent.atomic.AtomicReference

/** Activity to be started, when Application is not initialised yet (or needs re-initialization).
 * It allows to avoid "Application not responding" errors.
 * It is transparent and shows progress indicator only, launches next activity after application initialization.
 */
class FirstActivity() : AppCompatActivity(), Identifiable {
    override val instanceId: Long = InstanceId.next()

    enum class NeedToStart {
        HELP, CHANGELOG, OTHER
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(MyLocale.onAttachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
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
                .then("finishFirstActivity", true) {
                    finish()
                }
            MyAction.SET_DEFAULT_VALUES -> {
                setDefaultValuesOnUiThread(this)
                myContextHolder.future.then("finishFirstActivity", true) { finish() }
            }
            MyAction.CLOSE_ALL_ACTIVITIES -> finish()
            else -> {
                myContextHolder.initialize(this)
                    .thenTry("startNextActivity", true) { tryMyContext: Try<MyContext> ->
                        startNextActivity(tryMyContext, intent)
                    }
                    .then("finishFirstActivity", true) {
                        finish()
                    }
            }
        }
    }

    private fun startNextActivity(tryMyContext: Try<MyContext>, intent: Intent?) = tryMyContext.flatMap { myContext ->
        val msg = "startNextActivity by $instanceIdString"
        if ((myContext.isReady || myContext.state == MyContextState.UPGRADING) && !myContext.isExpired) {
            when (needToStartNext(this, myContext)) {
                NeedToStart.HELP -> HelpActivity.startMe(this, true, HelpActivity.PAGE_LOGO)
                NeedToStart.CHANGELOG -> HelpActivity.startMe(this, true, HelpActivity.PAGE_CHANGELOG)
                else -> {
                    val intent2 = Intent(this, TimelineActivity::class.java)
                    if (intent != null) {
                        val action = intent.action
                        if (!action.isNullOrEmpty()) {
                            intent2.action = action
                        }
                        val data = intent.data
                        if (data != null) {
                            intent2.data = data
                        }
                        val extras = intent.extras
                        if (extras != null) {
                            intent2.putExtras(extras)
                        }
                    }
                    startActivity(intent2)
                }
            }
            TryUtils.SUCCESS
        } else TryUtils.failure("$msg, MyContext is not ready: ${myContext.state}")
    }

    companion object {
        private val SET_DEFAULT_VALUES: String = "setDefaultValues"
        private val resultOfSettingDefaults: AtomicReference<TriState> = AtomicReference(TriState.UNKNOWN)

        fun restartApp(context: Context, calledBy: Any): MyFutureContext =
            myContextHolder.reInitialize(context, calledBy)
                .then("goHomeOnRestart", true) {
                    TimelineActivity.onAppStart.set(true)
                    goHome(context)
                }

        fun goHome(context: Context) {
            val method = "goHome with $context"
            try {
                MyLog.i(context, "Starting $method")
                val intent = Intent(context, FirstActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                val contextNow = myContextHolder.getNow().context
                // To avoid cycling
                if (contextNow == context) {
                    MyLog.w(context, "Failed $method, same as now", e)
                } else {
                    MyLog.i(context, "Failed $method, trying with $contextNow", e)
                    goHome(contextNow)
                }
            }
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

        private val mutexSetDefaultValues = Mutex()

        /** @return success
         */
        suspend fun setDefaultValues(context: Context?): Boolean {
            if (context == null) {
                MyLog.e(FirstActivity::class, SET_DEFAULT_VALUES + " no context")
                return false
            }
            mutexSetDefaultValues.withLock {
                resultOfSettingDefaults.set(TriState.UNKNOWN)
                try {
                    if (context is Activity) {
                        context.runOnUiThread { setDefaultValuesOnUiThread(context) }
                    } else {
                        startMeAsync(context, MyAction.SET_DEFAULT_VALUES)
                    }
                    for (i in 0..99) {
                        delay(50)
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
