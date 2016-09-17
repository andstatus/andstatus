/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.text.TextUtils;
import android.util.Base64;

import org.acra.ACRA;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yvolk@yurivolkov.com
 * Based on https://www.airpair.com/android/posts/adding-tampering-detection-to-your-android-app
 */
public class TamperingDetector {

    private static final Map<String, String> knownSignatures = new HashMap<>();
    private static volatile String knownAppSignature = "";

    static {
        knownSignatures.put("Q2LVj1MPgZdoNckr4g0WbOmi7nE=", "release-keys");
        knownSignatures.put("U+TELzORnTCJ/2OZKoIbcNhVMPg=", "debug-keys");
        knownSignatures.put("qhfrV6COj1j+fUrohD3xVZpfhYg=", "f-droid-keys");
    }

    public static void initialize(Context context) {
        StringBuilder builder = new StringBuilder();
        for (String signature : getAppSignatures(context)) {
            if (knownSignatures.containsKey(signature)) {
                knownAppSignature = knownSignatures.get(signature);
                I18n.appendWithSpace(builder, "(" + knownAppSignature + ")");
            } else {
                MyLog.i(TamperingDetector.class, "Unknown APK signature:'" + signature + "'");
                I18n.appendWithSpace(builder, signature);
            }
        }
        ACRA.getErrorReporter().putCustomData("apkSignatures", builder.toString());
    }

    private static List<String> getAppSignatures(Context context) {
        List<String> signatures = new ArrayList<>();
        try {
            PackageInfo packageInfo = context.getPackageManager().
                    getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : packageInfo.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String signatureString = Base64.encodeToString(md.digest(), Base64.DEFAULT);
                signatureString = signatureString.substring(0, signatureString.length() - 1);
                signatures.add(signatureString);
            }
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            MyLog.e(TamperingDetector.class, e);
        }
        return signatures;
    }

    public static boolean hasKnownAppSignature() {
        return !TextUtils.isEmpty(knownAppSignature);
    }

    public static String getAppSignatureInfo() {
        if (hasKnownAppSignature()) {
            return knownAppSignature;
        }
        return "unknown-keys";
    }
}