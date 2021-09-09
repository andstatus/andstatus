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

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
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
import org.andstatus.app.notification.Notifier.Companion.beep
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
class OriginEditor : MyActivity(OriginEditor::class) {
    private var builder: Origin.Builder? = null
    private var buttonSave: Button? = null
    private var buttonDelete: Button? = null
    private val originTypes: SelectableEnumList<OriginType> = SelectableEnumList.newInstance<OriginType>(OriginType::class.java)
    private var spinnerOriginType: Spinner? = null
    private var editTextOriginName: EditText? = null
    private var editTextHost: EditText? = null
    private var checkBoxIsSsl: CheckBox? = null
    internal var spinnerSslMode: Spinner? = null
    private var spinnerMentionAsWebFingerId: Spinner? = null
    private var spinnerUseLegacyHttpProtocol: Spinner? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        MyServiceManager.setServiceUnavailable()
        val builder = Origin.Builder( MyContextHolder.myContextHolder.getNow(), OriginType.GNUSOCIAL)
        this.builder = builder
        mLayoutId = R.layout.origin_editor
        super.onCreate(savedInstanceState)
        buttonSave = findViewById<View?>(R.id.button_save) as Button
        val buttonDiscard = findViewById<View?>(R.id.button_discard) as Button
        buttonDiscard.setOnClickListener { v: View? -> finish() }
        buttonDelete = (findViewById<View?>(R.id.button_delete) as Button).also { button ->
            button.setOnClickListener({ v: View ->
                if (builder.build().hasNotes()) {
                    DialogFactory.showOkCancelDialog(this, String.format(getText(R.string.delete_origin_dialog_title).toString(), builder.build().name),
                            getText(R.string.delete_origin_dialog_text)) { confirmed: Boolean -> deleteOrigin(confirmed) }
                } else {
                    deleteOrigin(true)
                }
            })
        }
        spinnerOriginType = (findViewById<View?>(R.id.origin_type) as Spinner).also { spinner ->
            spinner.setAdapter(originTypes.getSpinnerArrayAdapter(this))
        }
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
        CompletableFuture.supplyAsync({ builder?.delete() }, NonUiThreadExecutor.INSTANCE)
                .thenAcceptAsync({ ok: Boolean? ->
                    if (ok == true) {
                        setResult(RESULT_OK)
                        finish()
                    }
                }, UiThreadExecutor.INSTANCE)
    }

    private fun processNewIntent(intentNew: Intent) {
        val editorAction = intentNew.getAction()
        if (Intent.ACTION_INSERT == editorAction) {
            buttonSave?.setOnClickListener(AddOrigin())
            buttonSave?.setText(R.string.button_add)
            val origin = DiscoveredOrigins.fromName(intentNew.getStringExtra(IntentExtra.ORIGIN_NAME.key))
            if (origin.isValid) {
                builder = Origin.Builder(origin)
            } else {
                val originType: OriginType = OriginType.fromCode(intentNew.getStringExtra(IntentExtra.ORIGIN_TYPE.key))
                builder = Origin.Builder(
                    MyContextHolder.myContextHolder.getNow(),
                    if (OriginType.UNKNOWN == originType) OriginType.GNUSOCIAL else originType
                )
                if (OriginType.UNKNOWN != originType) {
                    spinnerOriginType?.setEnabled(false)
                }
            }
        } else {
            buttonSave?.setOnClickListener(SaveOrigin())
            spinnerOriginType?.setEnabled(false)
            editTextOriginName?.setEnabled(false)
            val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins.fromName(
                    intentNew.getStringExtra(IntentExtra.ORIGIN_NAME.key))
            builder = Origin.Builder(origin)
        }
        val origin = builder?.build() ?: Origin.EMPTY
        MyLog.v(this) { "processNewIntent: $origin" }
        spinnerOriginType?.let { spinner ->
            spinner.setSelection(originTypes.getIndex(origin.originType))
            if (spinner.isEnabled()) {
                spinner.setOnItemSelectedListener(object : OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (builder?.build()?.originType != originTypes[spinner.getSelectedItemPosition()]) {
                            intentNew.putExtra(IntentExtra.ORIGIN_TYPE.key,
                                    originTypes[spinner.getSelectedItemPosition()].getCode())
                            processNewIntent(intentNew)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                })
            }
        }
        editTextOriginName?.setText(origin.name)
        val showHostOrUrl = origin.shouldHaveUrl()
        if (showHostOrUrl) {
            val strHostOrUrl: String?
            strHostOrUrl = when {
                UrlUtils.isHostOnly(origin.url) -> {
                    origin.url?.host
                }
                origin.url != null -> {
                    origin.url?.toExternalForm()
                }
                else -> {
                    ""
                }
            }
            editTextHost?.setText(strHostOrUrl)
            editTextHost?.setHint(origin.alternativeTermForResourceId(R.string.host_hint))
            if (Intent.ACTION_INSERT == editorAction && origin.name.isEmpty()) {
                editTextHost?.setOnFocusChangeListener({ v: View?, hasFocus: Boolean ->
                    if (!hasFocus) {
                        originNameFromHost()
                    }
                })
            }
            MyUrlSpan.showLabel(this, R.id.label_host, origin.alternativeTermForResourceId(R.string.label_host))
        } else {
            ViewUtils.showView(this, R.id.label_host, false)
        }
        ViewUtils.showView(editTextHost, showHostOrUrl)
        MyCheckBox[this, R.id.is_ssl, origin.isSsl()] = { buttonView, isChecked -> showSslMode(isChecked) }
        spinnerSslMode?.setSelection(origin.getSslMode().getEntriesPosition())
        spinnerSslMode?.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                showSslModeSummary(SslModeEnum.fromEntriesPosition(position))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Empty
            }
        })
        showSslModeSummary(origin.getSslMode())
        showSslMode(origin.isSsl())
        MyCheckBox.setEnabled(this, R.id.allow_html, origin.isHtmlContentAllowed())
        spinnerMentionAsWebFingerId?.setSelection(origin.getMentionAsWebFingerId().getEntriesPosition())
        spinnerUseLegacyHttpProtocol?.setSelection(origin.useLegacyHttpProtocol().getEntriesPosition())
        buttonDelete?.setVisibility(if (origin.hasAccounts()) View.GONE else View.VISIBLE)
        MyCheckBox[this, R.id.in_combined_global_search, origin.isInCombinedGlobalSearch()] = origin.originType.isSearchTimelineSyncable()
        MyCheckBox[this, R.id.in_combined_public_reload, origin.isInCombinedPublicReload()] = origin.originType.isPublicTimeLineSyncable()
        var title = getText(R.string.label_origin_system).toString()
        if (origin.isPersistent()) {
            title = origin.name + " - " + title
        }
        setTitle(title)
    }

    fun originNameFromHost() {
        if (TextUtils.isEmpty(editTextOriginName?.getText())) builder?.let { builder ->
            val origin = Origin.Builder(
                    builder.getMyContext(), originTypes[spinnerOriginType?.getSelectedItemPosition() ?: -1])
                    .setHostOrUrl(editTextHost?.getText().toString()).build()
            if (origin.url != null) {
                editTextOriginName?.setText(origin.url?.host)
            }
        }
    }

    fun showSslModeSummary(sslMode: SslModeEnum) {
        (findViewById<View?>(R.id.ssl_mode_summary) as TextView).setText(sslMode.getSummaryResourceId())
    }

    fun showSslMode(isSsl: Boolean) {
        findViewById<View?>(R.id.ssl_mode_container).visibility = if (isSsl) View.VISIBLE else View.GONE
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processNewIntent(intent)
    }

    private inner class AddOrigin : View.OnClickListener {
        override fun onClick(v: View?) {
            originNameFromHost()
            builder?.let { oldBuilder ->
                builder = Origin.Builder(oldBuilder.getMyContext(), originTypes[spinnerOriginType?.getSelectedItemPosition() ?: -1])
                        .setName(editTextOriginName?.getText().toString())
            }
            saveOthers()
        }
    }

    private inner class SaveOrigin : View.OnClickListener {
        override fun onClick(v: View?) {
            saveOthers()
        }
    }

    private fun saveOthers() {
        (this.builder ?: return)
        .setHostOrUrl(editTextHost?.getText().toString())
        .setSsl(checkBoxIsSsl?.isChecked() ?: true)
        .setSslMode(SslModeEnum.fromEntriesPosition(spinnerSslMode?.getSelectedItemPosition() ?: -1))
        .setHtmlContentAllowed(MyCheckBox.isChecked(this, R.id.allow_html, false))
        .setMentionAsWebFingerId(
            TriState.fromEntriesPosition(
                spinnerMentionAsWebFingerId?.getSelectedItemPosition() ?: -1
            )
        )
        .setUseLegacyHttpProtocol(
            TriState.fromEntriesPosition(
                spinnerUseLegacyHttpProtocol?.getSelectedItemPosition() ?: -1
            )
        )
        .setInCombinedGlobalSearch(MyCheckBox.isChecked(this, R.id.in_combined_global_search, false))
        .setInCombinedPublicReload(MyCheckBox.isChecked(this, R.id.in_combined_public_reload, false))
        .save()
        .let { builder ->
            MyLog.v(this) { (if (builder.isSaved()) "Saved" else "Not saved") + ": " + builder.build().toString() }
            if (builder.isSaved()) {
                builder.getMyContext().origins.initialize()
                setResult(RESULT_OK)
                finish()
            } else {
                beep(this)
            }
        }
    }
}
