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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.lang.SelectableEnumList;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;
import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;

/**
 * @author yvolk@yurivolkov.com
 */
public class OriginEditorTest extends ActivityTest<OriginEditor> {

    @Override
    protected Class<OriginEditor> getActivityClass() {
        TestSuite.initialize(this);
        return OriginEditor.class;
    }

    @Test
    public void test() throws InterruptedException {
        OriginType originType = OriginType.GNUSOCIAL;
        String originName = "oe" + System.currentTimeMillis();
        String host = originName + ".example.com";
        boolean isSsl = false;
        boolean allowHtml = true;
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.SECURE, allowHtml, TriState.UNKNOWN,
                TriState.UNKNOWN,
                true, true);
        
        host = originName + ".some.example.com";
        isSsl = true;
        allowHtml = false;
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.INSECURE, allowHtml, TriState.UNKNOWN,
                TriState.UNKNOWN,
                false, true);

        host = originName + ". я badhost.example.com";
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.MISCONFIGURED, allowHtml, TriState.UNKNOWN,
                TriState.TRUE,
                true, false);

        host = "http://" + originName + ".fourth. example. com ";
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.SECURE, allowHtml, TriState.UNKNOWN,
                TriState.FALSE,
                false, false);

        host = "http://" + originName + ".fifth.example.com/status";
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.INSECURE, allowHtml, TriState.UNKNOWN,
                TriState.UNKNOWN,
                true, true);
    }
    
    private void forOneOrigin(final OriginType originType, final String originName,
            final String hostOrUrl, final boolean isSsl, final SslModeEnum sslMode, 
            final boolean allowHtml, final TriState mentionAsWebFingerId,
            final TriState useLegacyHttpProtocol,
            final boolean inCombinedGlobalSearch, final boolean inCombinedPublicReload) {
        final String method = "OriginEditorTest";

        final Origin originOld = MyContextHolder.get().origins().fromName(originName);
        final Intent intent = new Intent();
        if (originOld.isPersistent()) {
            intent.setAction(Intent.ACTION_EDIT);
            intent.putExtra(IntentExtra.ORIGIN_NAME.key, originOld.getName());
        } else {
            intent.setAction(Intent.ACTION_INSERT);
        }

        final OriginEditor activity = getActivity();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // This is needed because the activity is actually never
                // destroyed
                // and onCreate occurs only once.
                activity.onNewIntent(intent);
            }
        }
        );
        getInstrumentation().waitForIdleSync();
        DbUtils.waitMs(method, 200);

        final Button buttonSave = activity.findViewById(R.id.button_save);
        final Spinner spinnerOriginType = activity.findViewById(R.id.origin_type);
        final EditText editTextOriginName = activity.findViewById(R.id.origin_name);
        final EditText editTextHost = activity.findViewById(R.id.host);
        final CheckBox checkBoxIsSsl = activity.findViewById(R.id.is_ssl);
        final Spinner spinnerSslMode = activity.findViewById(R.id.ssl_mode);
        final CheckBox checkBoxAllowHtml = activity.findViewById(R.id.allow_html);
        final Spinner spinnerMentionAsWebFingerId = activity.findViewById(R.id.mention_as_webfingerid);
        final Spinner spinnerUseLegacyHttpProtocol = activity.findViewById(R.id.use_legacy_http_protocol);

        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                spinnerOriginType.setSelection(SelectableEnumList.newInstance(OriginType.class).getIndex(originType));
                editTextOriginName.setText(originName);
                editTextHost.setText(hostOrUrl);
                checkBoxIsSsl.setChecked(isSsl);
                spinnerSslMode.setSelection(sslMode.getEntriesPosition());
                checkBoxAllowHtml.setChecked(allowHtml);
                spinnerMentionAsWebFingerId.setSelection(mentionAsWebFingerId.getEntriesPosition());
                spinnerUseLegacyHttpProtocol.setSelection(useLegacyHttpProtocol.getEntriesPosition());
                ((CheckBox) activity.findViewById(R.id.in_combined_global_search)).
                        setChecked(inCombinedGlobalSearch);
                ((CheckBox) activity.findViewById(R.id.in_combined_public_reload)).
                        setChecked(inCombinedPublicReload);
                DbUtils.waitMs(method, 1000);
                MyLog.v(this, method + "-Log before click");
                buttonSave.performClick();
            }
        };

        MyLog.v(this, method + "-Log before run clicker 1");
        activity.runOnUiThread(clicker);
        getInstrumentation().waitForIdleSync();
        DbUtils.waitMs(method, 200);

        Origin origin = MyContextHolder.get().origins().fromName(originName);
        assertEquals("Origin '" + originName + "' added", originName, origin.getName());
        assertEquals(originType, origin.getOriginType());
        if (hostOrUrl.contains("bad")) {
            assertEquals(originOld.getUrl(), origin.getUrl());
        } else {
            URL url2 = UrlUtils.buildUrl(hostOrUrl, isSsl);
            if (!url2.equals(origin.getUrl())) {
                if (!UrlUtils.isHostOnly(url2) && !hostOrUrl.endsWith("/")) {
                    assertEquals(UrlUtils.buildUrl(hostOrUrl + "/", isSsl), origin.getUrl());
                } else {
                    assertEquals(UrlUtils.buildUrl(hostOrUrl, isSsl), origin.getUrl());
                }
            }
        }
        assertEquals(isSsl, origin.isSsl());
        assertEquals(sslMode, origin.getSslMode());
        assertEquals(allowHtml, origin.isHtmlContentAllowed());
        assertEquals(mentionAsWebFingerId, origin.getMentionAsWebFingerId());
        assertEquals(useLegacyHttpProtocol, origin.useLegacyHttpProtocol());
        assertEquals(inCombinedGlobalSearch, origin.isInCombinedGlobalSearch());
        assertEquals(inCombinedPublicReload, origin.isInCombinedPublicReload());
    }
}
