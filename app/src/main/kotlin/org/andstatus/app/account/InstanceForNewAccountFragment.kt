/* 
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.vavr.control.Try
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UrlUtils

class InstanceForNewAccountFragment : Fragment() {
    private var originType: OriginType = OriginType.UNKNOWN
    private var origin: Origin = Origin.EMPTY
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.instance_for_new_account, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val activity = activity as AccountSettingsActivity?
        if (activity != null) {
            origin = activity.state.myAccount.origin
            originType =
                if (origin.nonEmpty) origin.originType else OriginType.fromCode(arguments?.getString(IntentExtra.ORIGIN_TYPE.key))
            prepareScreen(activity)
            activity.updateScreen()
            if (origin.isPersistent()) {
                MyLog.i(this, "Launching verifyCredentials")
                activity.verifyCredentials(true)
            }
        }
        super.onActivityCreated(savedInstanceState)
    }

    private fun prepareScreen(activity: AccountSettingsActivity) {
        val instanceTextView = activity.findFragmentViewById(R.id.originInstance) as TextView?
        if (instanceTextView != null) {
            instanceTextView.hint = getText(R.string.hint_which_instance).toString() + "\n\n" +
                getText(if (originType === OriginType.MASTODON) R.string.host_hint_mastodon else R.string.host_hint)
            if (origin.nonEmpty) {
                instanceTextView.text = origin.getHost()
            }
        }
        val buttonText = String.format(
            getText(R.string.log_in_to_social_network).toString(),
            originType.title(activity)
        )
        val buttonTextView = activity.showTextView(R.id.log_in_to_social_network, buttonText, true)
        buttonTextView?.setOnClickListener { view: View? -> onLogInClick(view) }
    }

    private fun onLogInClick(view: View?) {
        val activity = activity as AccountSettingsActivity? ?: return
        activity.clearError()
        getNewOrExistingOrigin(activity)
            .onFailure { e: Throwable ->
                activity.appendError(e.message)
                activity.showErrors()
            }
            .onSuccess { originNew: Origin -> onNewOrigin(activity, originNew) }
    }

    private fun getNewOrExistingOrigin(activity: AccountSettingsActivity): Try<Origin> {
        val instanceTextView = activity.findFragmentViewById(R.id.originInstance) as TextView?
            ?: return TryUtils.failure("No text view ???")
        val hostOrUrl = instanceTextView.text.toString()
        val url1 = UrlUtils.buildUrl(hostOrUrl, true)
            ?: return TryUtils.failure(getText(R.string.error_invalid_value).toString() + ": '" + hostOrUrl + "'")
        val host = url1.host
        for (existing in myContextHolder.getBlocking().origins.originsOfType(originType)) {
            if (host == existing.getHost()) {
                return Try.success(existing)
            }
        }
        val origin = Origin.Builder(myContextHolder.getBlocking(), originType)
            .setHostOrUrl(hostOrUrl)
            .setName(host)
            .save()
            .build()
        return if (origin.isPersistent()) Try.success(origin) else TryUtils.failure(getText(R.string.error_invalid_value).toString() + ": " + origin)
    }

    private fun onNewOrigin(activity: AccountSettingsActivity, originNew: Origin) {
        if (originNew == origin || myContextHolder.getNow().isReady) {
            if (activity.state.myAccount.origin != originNew) {
                activity.state.builder.setOrigin(originNew)
            }
            activity.verifyCredentials(true)
        } else {
            // TODO: fewer logging below
            val future1: AsyncResult<MyContext, MyContext> = myContextHolder.initialize(activity).getFuture().future
            MyLog.d(this, "onNewOrigin After 'initialize' $future1")
            val future2: AsyncResult<MyContext, MyContext> =
                myContextHolder.whenSuccessAsync(true) { myContext: MyContext ->
                    activity.finish()
                    AccountSettingsActivity.startAddingNewAccount(myContext.context, originNew.name, true)
                }.getFuture().future
            MyLog.d(this, "onNewOrigin After 'whenSuccessAsync' $future2")
        }
    }
}
