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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
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
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UrlUtils;

/**
 * Add/Update Microblogging system
 * @author yvolk@yurivolkov.com
 */
public class OriginEditor extends Activity {
    private Origin.Builder builder;

    private Button buttonSave;
    private Button buttonDelete;
    private Spinner spinnerOriginType;
    private EditText editTextOriginName;
    private EditText editTextHost;
    private CheckBox checkBoxIsSsl;
    private Spinner spinnerSslMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MyPreferences.setThemedContentView(this, R.layout.origin_editor);

        buttonSave = (Button) findViewById(R.id.button_save);
        Button buttonDiscard = (Button) findViewById(R.id.button_discard);
        buttonDiscard.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        buttonDelete = (Button) findViewById(R.id.button_delete);
        buttonDelete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (builder.delete()) {
                    MyContextHolder.get().persistentOrigins().initialize();
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });
        
        spinnerOriginType = (Spinner) findViewById(R.id.origin_type);
        editTextOriginName = (EditText) findViewById(R.id.origin_name);
        editTextHost = (EditText) findViewById(R.id.host);
        checkBoxIsSsl = (CheckBox) findViewById(R.id.is_ssl);
        spinnerSslMode = (Spinner) findViewById(R.id.ssl_mode);

        processNewIntent(getIntent());
    }

    private void processNewIntent(Intent intentNew) {
        String editorAction = intentNew.getAction();
        
        if (Intent.ACTION_INSERT.equals(editorAction)) {
            buttonSave.setOnClickListener(new AddOrigin());
            buttonSave.setText(R.string.button_add);
            builder = new Origin.Builder(OriginType.GNUSOCIAL);
        } else {
            buttonSave.setOnClickListener(new SaveOrigin());
            spinnerOriginType.setEnabled(false);
            editTextOriginName.setEnabled(false);
            Origin origin = MyContextHolder.get().persistentOrigins().fromName(intentNew.getStringExtra(IntentExtra.EXTRA_ORIGIN_NAME.key));
            builder = new Origin.Builder(origin);
        }

        Origin origin = builder.build();
        MyLog.v(this, "processNewIntent: " + origin.toString());
        spinnerOriginType.setSelection(origin.originType.getEntriesPosition());
        editTextOriginName.setText(origin.getName());
        
        String strHost = "";
        if (UrlUtils.isHostOnly(origin.getUrl())) {
            strHost = origin.getUrl().getHost();
        } else if (origin.getUrl() != null) {
            strHost = origin.getUrl().toExternalForm();
        }
        editTextHost.setText(strHost);
        
        checkBoxIsSsl.setChecked(origin.isSsl());
        checkBoxIsSsl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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
        ((CheckBox) findViewById(R.id.allow_html)).setChecked(origin.isHtmlContentAllowed());
        
        buttonDelete.setVisibility(origin.hasChildren() ? View.GONE : View.VISIBLE);

        ((CheckBox) findViewById(R.id.in_combined_global_search)).setChecked(origin.isInCombinedGlobalSearch());
        ((CheckBox) findViewById(R.id.in_combined_public_reload)).setChecked(origin.isInCombinedPublicReload());
        
        String title = getText(R.string.label_origin_system).toString();
        if (origin.isPersistent()) {
            title = origin.getName() + " - " + title;
        }
        getActionBar().setTitle(title);
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
            builder = new Origin.Builder(OriginType.fromEntriesPosition(spinnerOriginType.getSelectedItemPosition()));
            builder.setName(editTextOriginName.getText().toString());
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
        builder.setHostOrUrl(editTextHost.getText().toString());
        builder.setSsl(checkBoxIsSsl.isChecked());
        builder.setSslMode(SslModeEnum.fromEntriesPosition(spinnerSslMode.getSelectedItemPosition()));
        builder.setHtmlContentAllowed(((CheckBox) findViewById(R.id.allow_html)).isChecked());
        builder.setInCombinedGlobalSearch(((CheckBox) findViewById(R.id.in_combined_global_search)).isChecked());
        builder.setInCombinedPublicReload(((CheckBox) findViewById(R.id.in_combined_public_reload)).isChecked());
        builder.save();
        MyLog.v(this, (builder.isSaved() ? "Saved" : "Not saved") + ": " + builder.build().toString());
        if (builder.isSaved()) {
            MyContextHolder.get().persistentOrigins().initialize();
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
            MyLog.e("beep", e);
        }        
    }
}
