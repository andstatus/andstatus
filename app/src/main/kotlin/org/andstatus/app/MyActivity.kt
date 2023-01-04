/*
 * Copyright (c) 2015-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.InflateException
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import io.vavr.control.Try
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextEmpty
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyLocale
import org.andstatus.app.context.MyTheme
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.Identified
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.Taggable.Companion.anyToTag
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yvolk@yurivolkov.com
 */
open class MyActivity(
    tag: Any,
    identifiable: Identifiable = Identified(anyToTag(tag))
) : AppCompatActivity(), Identifiable by identifiable {

    protected enum class OnFinishAction {
        RESTART_APP, RESTART_ME, DONE, NONE
    }

    // introduce this in order to avoid duplicated restarts: we have one place, where we restart anything
    protected var onFinishAction: AtomicReference<OnFinishAction> = AtomicReference(OnFinishAction.NONE)
    protected var mLayoutId = 0
    protected var myResumed = false

    /**
     * We are going to finish/restart this Activity (e.g. onResume or even onCreate)
     */
    @Volatile
    private var mFinishing = false
    private var mOptionsMenu: Menu? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(MyLocale.onAttachBaseContext(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        super.applyOverrideConfiguration(
            MyLocale.applyOverrideConfiguration(baseContext, overrideConfiguration)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MyLog.v(this) { "onCreate" + if (isFinishing) " finishing" else "" }
        MyTheme.loadTheme(this)
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        if (mLayoutId != 0) {
            try {
                MyTheme.setContentView(this, mLayoutId)
            } catch (e: InflateException) {
                val logMsg = ("Error inflating layoutId:$mLayoutId"
                    + if (previousErrorInflatingTime == 0L) ", going Home..." else ", again. Similar error occurred "
                    + RelativeTime.getDifference(this, previousErrorInflatingTime))
                MyLog.e(this, logMsg, e)
                if (previousErrorInflatingTime == 0L) {
                    previousErrorInflatingTime = System.currentTimeMillis()
                    finish()
                    MyContextHolder.myContextHolder.getNow().setExpired { logMsg }
                    FirstActivity.goHome(this)
                } else {
                    throw IllegalStateException(logMsg, e)
                }
                return
            }
        }
        findViewById<Toolbar?>(R.id.my_action_bar)?.let {
            setSupportActionBar(it)
        }
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        mOptionsMenu = menu
        return super.onCreateOptionsMenu(menu)
    }

    fun getOptionsMenu(): Menu? {
        return mOptionsMenu
    }

    fun setTitle(title: String?) {
        supportActionBar?.title = title
    }

    fun setSubtitle(subtitle: CharSequence?) {
        supportActionBar?.subtitle = subtitle
    }

    fun isMyResumed(): Boolean {
        return myResumed
    }

    override fun onPause() {
        myResumed = false
        super.onPause()
        toggleFullscreen(TriState.FALSE)
    }

    override fun onResume() {
        myResumed = true
        super.onResume()
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int) {
        try {
            super.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            val text: String = "startActivityForResult requestCode:$requestCode, intent:$intent\n" +
                "${e::class.qualifiedName}: ${e.message}"
            MyLog.w(this, text, e)
            DialogFactory.showOkAlertDialog(this, this, R.string.app_name, text)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Sets or Toggles fullscreen mode
     * REQUIRE: android:configChanges="orientation|screenSize"
     * Based on http://stackoverflow.com/a/30224178/297710
     * On Immersive mode: https://developer.android.com/training/system-ui/immersive.html
     */
    fun toggleFullscreen(fullScreenIn: TriState) {
        var uiOptionsNew = window.decorView.systemUiVisibility
        val fullscreenNew = if (fullScreenIn.known) fullScreenIn.toBoolean(false) else !isFullScreen()
        hideActionBar(fullscreenNew)
        if (fullscreenNew) {
            uiOptionsNew = uiOptionsNew or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            uiOptionsNew = uiOptionsNew or View.SYSTEM_UI_FLAG_FULLSCREEN
            uiOptionsNew = uiOptionsNew or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            uiOptionsNew = uiOptionsNew and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
            uiOptionsNew = uiOptionsNew and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            uiOptionsNew = uiOptionsNew and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
        }
        window.decorView.systemUiVisibility = uiOptionsNew
        onFullScreenToggle(fullscreenNew)
    }

    protected open fun onFullScreenToggle(fullscreenNew: Boolean) {}
    fun isFullScreen(): Boolean {
        val actionBar = supportActionBar
        return !(actionBar?.isShowing ?: (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0))
    }

    fun hideActionBar(hide: Boolean) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            if (hide) {
                actionBar.hide()
            } else {
                actionBar.show()
            }
        }
    }

    protected fun showFragment(fragmentClass: Class<out Fragment?>, args: Bundle?) {
        val classLoader = fragmentClass.classLoader
        if (classLoader == null) {
            MyLog.e(this, "No class loader for $fragmentClass")
        } else {
            val fragmentManager = supportFragmentManager
            val fragment = fragmentManager.fragmentFactory.instantiate(classLoader, fragmentClass.name)
            if (args != null) fragment.arguments = args
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragmentOne, fragment, "fragment").commit()
        }
    }

    /** Restart me if MyContext is not ready
     * @return failure if the Activity is finishing
     *         success MyContext Ready or EMPTY if not ready
     */
    fun myReadyContextOrRestartMe(): Try<MyContext> {
        val myContext = MyContextHolder.myContextHolder.tryReadyNow()
        var finishing = isFinishing
        if (myContext.isFailure) {
            if (initializeThenRestartActivity()) finishing = true
        }
        return if (finishing) {
            TryUtils.failure("Finishing...")
        } else myContext.recover(java.lang.Exception::class.java) { MyContextEmpty.EMPTY }
    }

    /** @return true if we are restarting
     */
    fun initializeThenRestartActivity(): Boolean {
        if (onFinishAction.compareAndSet(OnFinishAction.NONE, OnFinishAction.RESTART_ME)) {
            if (!isFinishing) finish()
            return true
        }
        return false
    }

    override fun finish() {
        val actionToDo = onFinishAction.getAndSet(OnFinishAction.DONE)
        if (actionToDo == OnFinishAction.DONE) {
            return
        }
        val isFinishing1 = isFinishing
        mFinishing = true
        MyLog.v(this) { "finish: " + onFinishAction.get() + if (isFinishing1) ", already finishing" else "" }
        if (!isFinishing1) {
            super.finish()
        }
        when (actionToDo) {
            OnFinishAction.RESTART_ME -> MyContextHolder.myContextHolder.initialize(this).thenStartActivity(this.intent)
            OnFinishAction.RESTART_APP -> MyContextHolder.myContextHolder.initialize(this).thenStartApp()
            else -> {}
        }
    }

    override fun isFinishing(): Boolean {
        return mFinishing || super.isFinishing()
    }

    companion object {
        @Volatile
        private var previousErrorInflatingTime: Long = 0
    }
}
