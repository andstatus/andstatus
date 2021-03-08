/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.origin

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.View.OnFocusChangeListener
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.lang.SelectableEnumList
import org.andstatus.app.net.http.SslModeEnum
import org.andstatus.app.os.NonUiThreadExecutor
import org.andstatus.app.os.UiThreadExecutor
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.util.MyCheckBox
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import org.andstatus.app.util.ViewUtils
import java.util.concurrent.CompletableFuture

/**
 * Add/Update Microblogging system
 * @author yvolk@yurivolkov.com
 */
class OriginEditor : MyActivity() {
    private var builder: Origin.Builder? = null
    private var buttonSave: Button? = null
    private var buttonDelete: Button? = null
    private val originTypes: SelectableEnumList<OriginType?>? = SelectableEnumList.Companion.newInstance<OriginType?>(OriginType::class.java)
    private var spinnerOriginType: Spinner? = null
    private var editTextOriginName: EditText? = null
    private var editTextHost: EditText? = null
    private var checkBoxIsSsl: CheckBox? = null
    private var spinnerSslMode: Spinner? = null
    private var spinnerMentionAsWebFingerId: Spinner? = null
    private var spinnerUseLegacyHttpProtocol: Spinner? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        MyServiceManager.Companion.setServiceUnavailable()
        builder = Origin.Builder( MyContextHolder.myContextHolder.getNow(), OriginType.GNUSOCIAL)
        mLayoutId = R.layout.origin_editor
        super.onCreate(savedInstanceState)
        buttonSave = findViewById<View?>(R.id.button_save) as Button
        val buttonDiscard = findViewById<View?>(R.id.button_discard) as Button
        buttonDiscard.setOnClickListener { v: View? -> finish() }
        buttonDelete = findViewById<View?>(R.id.button_delete) as Button
        buttonDelete.setOnClickListener(View.OnClickListener { v: View? ->
            if (builder.build().hasNotes()) {
                DialogFactory.showOkCancelDialog(this, String.format(getText(R.string.delete_origin_dialog_title).toString(), builder.build().name),
                        getText(R.string.delete_origin_dialog_text)) { confirmed: Boolean? -> deleteOrigin(confirmed) }
            } else {
                deleteOrigin(true)
            }
        })
        spinnerOriginType = findViewById<View?>(R.id.origin_type) as Spinner
        spinnerOriginType.setAdapter(originTypes.getSpinnerArrayAdapter(this))
        editTextOriginName = findViewById<View?>(R.id.origin_name) as EditText
        editTextHost = findViewById<View?>(R.id.host) as EditText
        checkBoxIsSsl = findViewById<View?>(R.id.is_ssl) as CheckBox
        spinnerSslMode = findViewById<View?>(R.id.ssl_mode) as Spinner
        spinnerMentionAsWebFingerId = findViewById<View?>(R.id.mention_as_webfingerid) as Spinner
        spinnerUseLegacyHttpProtocol = findViewById<View?>(R.id.use_legacy_http_protocol) as Spinner
        processNewIntent(intent)
    }

    private fun deleteOrigin(confirmed: Boolean) {
        if (!confirmed) return
        CompletableFuture.supplyAsync({ builder.delete() }, NonUiThreadExecutor.Companion.INSTANCE)
                .thenAcceptAsync({ ok: Boolean? ->
                    if (ok) {
                        setResult(RESULT_OK)
                        finish()
                    }
                }, UiThreadExecutor.Companion.INSTANCE)
    }

    private fun processNewIntent(intentNew: Intent?) {
        val editorAction = intentNew.getAction()
        if (Intent.ACTION_INSERT == editorAction) {
            buttonSave.setOnClickListener(AddOrigin())
            buttonSave.setText(R.string.button_add)
            val origin = DiscoveredOrigins.fromName(intentNew.getStringExtra(IntentExtra.ORIGIN_NAME.key))
            if (origin.isValid) {
                builder = Origin.Builder(origin)
            } else {
                val originType: OriginType = OriginType.Companion.fromCode(intentNew.getStringExtra(IntentExtra.ORIGIN_TYPE.key))
                builder = Origin.Builder( MyContextHolder.myContextHolder.getNow(), if (OriginType.UNKNOWN == originType) OriginType.GNUSOCIAL else originType)
                if (OriginType.UNKNOWN != originType) {
                    spinnerOriginType.setEnabled(false)
                }
            }
        } else {
            buttonSave.setOnClickListener(SaveOrigin())
            spinnerOriginType.setEnabled(false)
            editTextOriginName.setEnabled(false)
            val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins().fromName(
                    intentNew.getStringExtra(IntentExtra.ORIGIN_NAME.key))
            builder = Origin.Builder(origin)
        }
        val origin = builder.build()
        MyLog.v(TAG) { "processNewIntent: $origin" }
        spinnerOriginType.setSelection(originTypes.getIndex(origin.originType))
        if (spinnerOriginType.isEnabled()) {
            spinnerOriginType.setOnItemSelectedListener(object : OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (builder.build().originType != originTypes.get(spinnerOriginType.getSelectedItemPosition())) {
                        intentNew.putExtra(IntentExtra.ORIGIN_TYPE.key,
                                originTypes.get(spinnerOriginType.getSelectedItemPosition()).getCode())
                        processNewIntent(intentNew)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            })
        }
        editTextOriginName.setText(origin.name)
        val showHostOrUrl = origin.shouldHaveUrl()
        if (showHostOrUrl) {
            val strHostOrUrl: String?
            strHostOrUrl = if (UrlUtils.isHostOnly(origin.getUrl())) {
                origin.getUrl().host
            } else if (origin.getUrl() != null) {
                origin.getUrl().toExternalForm()
            } else {
                ""
            }
            editTextHost.setText(strHostOrUrl)
            editTextHost.setHint(origin.alternativeTermForResourceId(R.string.host_hint))
            if (Intent.ACTION_INSERT == editorAction && origin.name.isNullOrEmpty()) {
                editTextHost.setOnFocusChangeListener(OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                    if (!hasFocus) {
                        originNameFromHost()
                    }
                })
            }
            MyUrlSpan.Companion.showLabel(this, R.id.label_host, origin.alternativeTermForResourceId(R.string.label_host))
        } else {
            ViewUtils.showView(this, R.id.label_host, false)
        }
        ViewUtils.showView(editTextHost, showHostOrUrl)
        MyCheckBox.set(this, R.id.is_ssl, origin.isSsl) { buttonView, isChecked -> showSslMode(isChecked) }
        spinnerSslMode.setSelection(origin.sslMode.entriesPosition)
        spinnerSslMode.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                showSslModeSummary(SslModeEnum.Companion.fromEntriesPosition(position))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Empty
            }
        })
        showSslModeSummary(origin.sslMode)
        showSslMode(origin.isSsl)
        MyCheckBox.setEnabled(this, R.id.allow_html, origin.isHtmlContentAllowed)
        spinnerMentionAsWebFingerId.setSelection(origin.mentionAsWebFingerId.entriesPosition)
        spinnerUseLegacyHttpProtocol.setSelection(origin.useLegacyHttpProtocol().entriesPosition)
        buttonDelete.setVisibility(if (origin.hasAccounts()) View.GONE else View.VISIBLE)
        MyCheckBox.set(this, R.id.in_combined_global_search, origin.isInCombinedGlobalSearch,
                origin.originType.isSearchTimelineSyncable)
        MyCheckBox.set(this, R.id.in_combined_public_reload, origin.isInCombinedPublicReload,
                origin.originType.isPublicTimeLineSyncable)
        var title = getText(R.string.label_origin_system).toString()
        if (origin.isPersistent) {
            title = origin.name + " - " + title
        }
        setTitle(title)
    }

    fun originNameFromHost() {
        if (TextUtils.isEmpty(editTextOriginName.getText())) {
            val origin = Origin.Builder(
                    builder.getMyContext(), originTypes.get(spinnerOriginType.getSelectedItemPosition()))
                    .setHostOrUrl(editTextHost.getText().toString()).build()
            if (origin.getUrl() != null) {
                editTextOriginName.setText(origin.getUrl().host)
            }
        }
    }

    fun showSslModeSummary(sslMode: SslModeEnum?) {
        (findViewById<View?>(R.id.ssl_mode_summary) as TextView).setText(sslMode.getSummaryResourceId())
    }

    fun showSslMode(isSsl: Boolean) {
        findViewById<View?>(R.id.ssl_mode_container).visibility = if (isSsl) View.VISIBLE else View.GONE
    }

    public override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        processNewIntent(intent)
    }

    private inner class AddOrigin : View.OnClickListener {
        override fun onClick(v: View?) {
            originNameFromHost()
            builder = Origin.Builder(builder.getMyContext(), originTypes.get(spinnerOriginType.getSelectedItemPosition()))
                    .setName(editTextOriginName.getText().toString())
            saveOthers()
        }
    }

    private inner class SaveOrigin : View.OnClickListener {
        override fun onClick(v: View?) {
            saveOthers()
        }
    }

    private fun saveOthers() {
        builder.setHostOrUrl(editTextHost.getText().toString())
                .setSsl(checkBoxIsSsl.isChecked())
                .setSslMode(SslModeEnum.Companion.fromEntriesPosition(spinnerSslMode.getSelectedItemPosition()))
                .setHtmlContentAllowed(MyCheckBox.isChecked(this, R.id.allow_html, false))
                .setMentionAsWebFingerId(TriState.Companion.fromEntriesPosition(
                        spinnerMentionAsWebFingerId.getSelectedItemPosition()))
                .setUseLegacyHttpProtocol(TriState.Companion.fromEntriesPosition(
                        spinnerUseLegacyHttpProtocol.getSelectedItemPosition()))
                .setInCombinedGlobalSearch(MyCheckBox.isChecked(this, R.id.in_combined_global_search, false))
                .setInCombinedPublicReload(MyCheckBox.isChecked(this, R.id.in_combined_public_reload, false))
                .save()
        MyLog.v(TAG) { (if (builder.isSaved()) "Saved" else "Not saved") + ": " + builder.build().toString() }
        if (builder.isSaved()) {
            builder.getMyContext().origins().initialize()
            setResult(RESULT_OK)
            finish()
        } else {
            beep(this)
        }
    }

    companion object {
        private val TAG: String? = OriginEditor::class.java.simpleName

        /**
         * See http://stackoverflow.com/questions/4441334/how-to-play-an-android-notification-sound/9622040
         */
        private fun beep(context: Context?) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, notification)
                r.play()
            } catch (e: Exception) {
                MyLog.w(TAG, "beep", e)
            }
        }
    }
}