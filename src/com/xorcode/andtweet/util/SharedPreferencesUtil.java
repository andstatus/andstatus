/*
 * Copyright (C) 2010 yvolk (Yuri Volkov), http://yurivolkov.com
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
package com.xorcode.andtweet.util;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;

import com.xorcode.andtweet.AndTweetService;

public class SharedPreferencesUtil {
    private static final String TAG = SharedPreferencesUtil.class.getSimpleName();

    /**
     * Delete the preferences file!
     * return Was the file deleted?
     * */
    public static boolean delete(Context context, String prefsFileName) {
        boolean isDeleted = false;

        // Delete preferences file
        java.io.File prefFile = new java.io.File("/data/data/"
                + context.getPackageName() + "/shared_prefs/" + prefsFileName
                + ".xml");
        if (prefFile.exists()) {
            // Commit any changes left
            SharedPreferences.Editor prefs = context.getSharedPreferences(
                    prefsFileName, MODE_PRIVATE).edit();
            if (prefs != null) {
                prefs.commit();
                prefs = null;
            }

            isDeleted = prefFile.delete();
            try {
                if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
                    Log.v(TAG, "The prefs file '" + prefFile.getCanonicalPath()
                            + "' was " + (isDeleted ? "" : "not ") + " deleted");
                }
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        } else {
            try {
                if (Log.isLoggable(AndTweetService.APPTAG, Log.DEBUG)) {
                    Log.d(TAG, "The prefs file '" + prefFile.getCanonicalPath()
                            + "' was not found");
                }
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
        return isDeleted;
    }
}
