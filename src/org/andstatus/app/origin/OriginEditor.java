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
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.R;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

/**
 * Add/Update Microblogging system
 * @author yvolk@yurivolkov.com
 */
public class OriginEditor extends Activity {
    private String editorAction = Intent.ACTION_DEFAULT;
    private Origin.Builder builder;

    private Button buttonSave;
    private Button buttonDelete;
    private Spinner spinnerOriginType;
    private EditText editTextOriginName;
    private EditText editTextHost;
    private CheckBox checkBoxIsSsl;
    private CheckBox checkBoxAllowHtml;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MyPreferences.loadTheme(this, this);
        setContentView(R.layout.origin_editor);

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
        checkBoxAllowHtml = (CheckBox) findViewById(R.id.allow_html);
        
        processNewIntent(getIntent());
    }

    private void processNewIntent(Intent intentNew) {
        editorAction = intentNew.getAction();
        
        if (Intent.ACTION_INSERT.equals(editorAction)) {
            buttonSave.setOnClickListener(new AddOrigin());
            buttonSave.setText(R.string.button_add);
            builder = new Origin.Builder(OriginType.STATUSNET);
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
        editTextHost.setText(origin.getHost());
        checkBoxIsSsl.setChecked(origin.isSsl());
        checkBoxAllowHtml.setChecked(origin.isHtmlAllowed());
        
        buttonDelete.setVisibility(origin.hasChildren() ? View.GONE : View.VISIBLE);
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
        builder.setHost(editTextHost.getText().toString());
        builder.setSsl(checkBoxIsSsl.isChecked());
        builder.setAllowHtml(checkBoxAllowHtml.isChecked());
        builder.save();
        MyLog.v(this, "After save: " + builder.build().toString());
        MyContextHolder.get().persistentOrigins().initialize();
        setResult(RESULT_OK);
        finish();
    }
}
