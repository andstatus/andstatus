/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.R;
import org.andstatus.app.TestSuite;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class OriginEditorTest extends ActivityInstrumentationTestCase2<OriginEditor> {

    public OriginEditorTest() {
        super(OriginEditor.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }

    public void test() throws InterruptedException {
        OriginType originType = OriginType.STATUSNET;
        String originName = "oe" + System.currentTimeMillis();
        String host = originName + ".example.com";
        boolean isSsl = false;
        boolean allowHtml = true;
        forOneOrigin(originType, originName, host, isSsl, allowHtml);
        
        host = originName + ".some.example.com";
        isSsl = true;
        allowHtml = false;
        forOneOrigin(originType, originName, host, isSsl, allowHtml);

        host = originName + ". badhost.example.com";
        forOneOrigin(originType, originName, host, isSsl, allowHtml);
    }
    
    public void forOneOrigin(final OriginType originType, final String originName,
            final String host, final boolean isSsl, final boolean allowHtml)
            throws InterruptedException {
        final String method = "OriginEditorTest";

        final Origin originOld = MyContextHolder.get().persistentOrigins().fromName(originName);
        final Intent intent = new Intent();
        if (originOld.isPersistent()) {
            intent.setAction(Intent.ACTION_EDIT);
            intent.putExtra(IntentExtra.EXTRA_ORIGIN_NAME.key, originOld.getName());
        } else {
            intent.setAction(Intent.ACTION_INSERT);
        }
        setActivityIntent(intent);
        
        final OriginEditor activity = getActivity();

        final Button buttonSave = (Button) activity.findViewById(R.id.button_save);
        final Spinner spinnerOriginType = (Spinner) activity.findViewById(R.id.origin_type);
        final EditText editTextOriginName = (EditText) activity.findViewById(R.id.origin_name);
        final EditText editTextHost = (EditText) activity.findViewById(R.id.host);
        final CheckBox checkBoxIsSsl = (CheckBox) activity.findViewById(R.id.is_ssl);
        final CheckBox checkBoxAllowHtml = (CheckBox) activity.findViewById(R.id.allow_html);

        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                // This is needed because the activity is actually never destroyed
                // and onCreate occurs only once.
                activity.onNewIntent(intent);

                spinnerOriginType.setSelection(originType.getEntriesPosition());
                editTextOriginName.setText(originName);
                editTextHost.setText(host);
                checkBoxIsSsl.setChecked(isSsl);
                checkBoxAllowHtml.setChecked(allowHtml);

                MyLog.v(this, method + "-Log before click");
                buttonSave.performClick();
            }
        };

        MyLog.v(this, method + "-Log before run clicker 1");
        activity.runOnUiThread(clicker);
        Thread.sleep(2000);

        Origin origin = MyContextHolder.get().persistentOrigins().fromName(originName);
        assertEquals("Origin '" + originName + "' added", originName, origin.getName());
        assertEquals(originType, origin.getOriginType());
        if (host.contains("bad")) {
            assertEquals(originOld.getHost(), origin.getHost());
        } else {
            assertEquals(host, origin.getHost());
        }
        assertEquals(isSsl, origin.isSsl());
        assertEquals(allowHtml, origin.isHtmlAllowed());
    }
}
