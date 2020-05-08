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

package org.andstatus.app.origin;

import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.lang.SelectableEnumList;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyCheckBox;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;
import org.andstatus.app.util.ViewUtils;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * Add/Update Microblogging system
 * @author yvolk@yurivolkov.com
 */
public class OriginEditor extends MyActivity {
    private static final String TAG = OriginEditor.class.getSimpleName();
    private Origin.Builder builder;

    private Button buttonSave;
    private Button buttonDelete;
    private final SelectableEnumList<OriginType> originTypes = SelectableEnumList.newInstance(OriginType.class);
    private Spinner spinnerOriginType;
    private EditText editTextOriginName;
    private EditText editTextHost;
    private CheckBox checkBoxIsSsl;
    private Spinner spinnerSslMode;
    private Spinner spinnerMentionAsWebFingerId;
    private Spinner spinnerUseLegacyHttpProtocol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MyServiceManager.setServiceUnavailable();

        builder = new Origin.Builder(myContextHolder.getNow(), OriginType.GNUSOCIAL);
        mLayoutId = R.layout.origin_editor;
        super.onCreate(savedInstanceState);

        buttonSave = (Button) findViewById(R.id.button_save);
        Button buttonDiscard = (Button) findViewById(R.id.button_discard);
        buttonDiscard.setOnClickListener(v -> finish());
        buttonDelete = (Button) findViewById(R.id.button_delete);
        buttonDelete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (builder.delete()) {
                    builder.getMyContext().origins().initialize();
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });
        
        spinnerOriginType = (Spinner) findViewById(R.id.origin_type);
        spinnerOriginType.setAdapter(originTypes.getSpinnerArrayAdapter(this));
        editTextOriginName = (EditText) findViewById(R.id.origin_name);
        editTextHost = (EditText) findViewById(R.id.host);
        checkBoxIsSsl = (CheckBox) findViewById(R.id.is_ssl);
        spinnerSslMode = (Spinner) findViewById(R.id.ssl_mode);
        spinnerMentionAsWebFingerId = (Spinner) findViewById(R.id.mention_as_webfingerid);
        spinnerUseLegacyHttpProtocol = (Spinner) findViewById(R.id.use_legacy_http_protocol);

        processNewIntent(getIntent());
    }

    private void processNewIntent(final Intent intentNew) {
        String editorAction = intentNew.getAction();
        
        if (Intent.ACTION_INSERT.equals(editorAction)) {
            buttonSave.setOnClickListener(new AddOrigin());
            buttonSave.setText(R.string.button_add);
            Origin origin = DiscoveredOrigins.fromName(intentNew.getStringExtra(IntentExtra.ORIGIN_NAME.key));
            if (origin.isValid()) {
                builder = new Origin.Builder(origin);
            } else {
                OriginType originType = OriginType.fromCode(intentNew.getStringExtra(IntentExtra.ORIGIN_TYPE.key));
                builder = new Origin.Builder(myContextHolder.getNow(), OriginType.UNKNOWN.equals(originType) ? OriginType.GNUSOCIAL : originType);
                if (!OriginType.UNKNOWN.equals(originType)) {
                    spinnerOriginType.setEnabled(false);
                }
            }
        } else {
            buttonSave.setOnClickListener(new SaveOrigin());
            spinnerOriginType.setEnabled(false);
            editTextOriginName.setEnabled(false);
            Origin origin = myContextHolder.getNow().origins().fromName(
                    intentNew.getStringExtra(IntentExtra.ORIGIN_NAME.key));
            builder = new Origin.Builder(origin);
        }

        Origin origin = builder.build();
        MyLog.v(TAG, () -> "processNewIntent: " + origin.toString());
        spinnerOriginType.setSelection(originTypes.getIndex(origin.getOriginType()));
        if (spinnerOriginType.isEnabled()) {
            spinnerOriginType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (!builder.build().getOriginType().equals(
                            originTypes.get(spinnerOriginType.getSelectedItemPosition()))) {
                        intentNew.putExtra(IntentExtra.ORIGIN_TYPE.key,
                                originTypes.get(spinnerOriginType.getSelectedItemPosition()).getCode());
                        processNewIntent(intentNew);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        }

        editTextOriginName.setText(origin.getName());
        
        final boolean showHostOrUrl = origin.shouldHaveUrl();
        if (showHostOrUrl) {
            final String strHostOrUrl;
            if (UrlUtils.isHostOnly(origin.getUrl())) {
                strHostOrUrl = origin.getUrl().getHost();
            } else if (origin.getUrl() != null) {
                strHostOrUrl = origin.getUrl().toExternalForm();
            } else {
                strHostOrUrl = "";
            }
            editTextHost.setText(strHostOrUrl);
            editTextHost.setHint(origin.alternativeTermForResourceId(R.string.host_hint));

            if (Intent.ACTION_INSERT.equals(editorAction) && StringUtil.isEmpty(origin.getName())) {
                editTextHost.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        originNameFromHost();
                    }
                });
            }

            MyUrlSpan.showLabel(this, R.id.label_host, origin.alternativeTermForResourceId(R.string.label_host));
        } else {
            ViewUtils.showView(this, R.id.label_host, false);
        }
        ViewUtils.showView(editTextHost, showHostOrUrl);

        MyCheckBox.set(this, R.id.is_ssl, origin.isSsl() , new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                showSslMode(isChecked);
            }
        });
        spinnerSslMode.setSelection(origin.getSslMode().getEntriesPosition());
        spinnerSslMode.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showSslModeSummary(SslModeEnum.fromEntriesPosition(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Empty
            }
        });
        showSslModeSummary(origin.getSslMode());
        showSslMode(origin.isSsl());
        MyCheckBox.setEnabled(this, R.id.allow_html, origin.isHtmlContentAllowed());

        spinnerMentionAsWebFingerId.setSelection(origin.getMentionAsWebFingerId().getEntriesPosition());
        spinnerUseLegacyHttpProtocol.setSelection(origin.useLegacyHttpProtocol().getEntriesPosition());
        
        buttonDelete.setVisibility(origin.hasChildren() ? View.GONE : View.VISIBLE);

        MyCheckBox.set(this, R.id.in_combined_global_search, origin.isInCombinedGlobalSearch(),
                origin.getOriginType().isSearchTimelineSyncable());
        MyCheckBox.set(this, R.id.in_combined_public_reload, origin.isInCombinedPublicReload(),
                origin.getOriginType().isPublicTimeLineSyncable());

        String title = getText(R.string.label_origin_system).toString();
        if (origin.isPersistent()) {
            title = origin.getName() + " - " + title;
        }
        setTitle(title);
    }

    void originNameFromHost() {
        if (TextUtils.isEmpty(editTextOriginName.getText())) {
            Origin origin = new Origin.Builder(
                    builder.getMyContext(), originTypes.get(spinnerOriginType.getSelectedItemPosition()))
                    .setHostOrUrl(editTextHost.getText().toString()).build();
            if (origin.getUrl() != null) {
                editTextOriginName.setText(origin.getUrl().getHost());
            }
        }
    }

    void showSslModeSummary(SslModeEnum sslMode) {
        ((TextView)findViewById(R.id.ssl_mode_summary)).setText(sslMode.getSummaryResourceId());
    }
    
    void showSslMode(boolean isSsl) {
        findViewById(R.id.ssl_mode_container).setVisibility(isSsl ? View.VISIBLE : View.GONE);
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processNewIntent(intent);
    }

    private class AddOrigin implements OnClickListener {
        @Override
        public void onClick(View v) {
            originNameFromHost();
            builder = new Origin.Builder(builder.getMyContext(), originTypes.get(spinnerOriginType.getSelectedItemPosition()))
                    .setName(editTextOriginName.getText().toString());
            saveOthers();
        }
    }
    
    private class SaveOrigin implements OnClickListener {
        @Override
        public void onClick(View v) {
            saveOthers();
        }
    }
    
    private void saveOthers() {
        builder.setHostOrUrl(editTextHost.getText().toString())
                .setSsl(checkBoxIsSsl.isChecked())
                .setSslMode(SslModeEnum.fromEntriesPosition(spinnerSslMode.getSelectedItemPosition()))
                .setHtmlContentAllowed(MyCheckBox.isChecked(this, R.id.allow_html, false))
                .setMentionAsWebFingerId(TriState.fromEntriesPosition(
                        spinnerMentionAsWebFingerId.getSelectedItemPosition()))
                .setUseLegacyHttpProtocol(TriState.fromEntriesPosition(
                        spinnerUseLegacyHttpProtocol.getSelectedItemPosition()))
                .setInCombinedGlobalSearch(MyCheckBox.isChecked(this, R.id.in_combined_global_search, false))
                .setInCombinedPublicReload(MyCheckBox.isChecked(this, R.id.in_combined_public_reload, false))
                .save();
        MyLog.v(TAG, () -> (builder.isSaved() ? "Saved" : "Not saved") + ": " + builder.build().toString());
        if (builder.isSaved()) {
            builder.getMyContext().origins().initialize();
            setResult(RESULT_OK);
            finish();
        } else {
            beep(this);
        }
    }
    
    /**
     * See http://stackoverflow.com/questions/4441334/how-to-play-an-android-notification-sound/9622040
     */
    private static void beep(Context context) {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(context, notification);
            r.play();
        } catch (Exception e) {
            MyLog.w(TAG, "beep", e);
        }        
    }
}
