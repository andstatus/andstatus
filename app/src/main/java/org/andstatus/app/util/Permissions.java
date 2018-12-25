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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.andstatus.app.context.MyContextHolder;

/**
 * @author yvolk@yurivolkov.com
 * See http://developer.android.com/training/permissions/index.html
 */
public class Permissions {
    private static volatile boolean allGranted = false;

    public static void setAllGranted(boolean allGranted) {
        Permissions.allGranted = allGranted;
    }

    public enum PermissionType {
        READ_EXTERNAL_STORAGE(Manifest.permission.READ_EXTERNAL_STORAGE),
        WRITE_EXTERNAL_STORAGE(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        GET_ACCOUNTS(Manifest.permission.GET_ACCOUNTS);

        public final String manifestPermission;
        PermissionType(String manifestPermission) {
            this.manifestPermission = manifestPermission;
        }
    }

    private Permissions() {
        // Empty
    }

    /**
     * See http://stackoverflow.com/questions/30719047/android-m-check-runtime-permission-how-to-determine-if-the-user-checked-nev
     */
    public static void checkPermissionAndRequestIt(@NonNull Activity activity,
                                                   @NonNull PermissionType permissionType) {
        if (checkPermission(activity, permissionType)) {
            return;
        }
        if (!ActivityCompat.OnRequestPermissionsResultCallback.
                class.isAssignableFrom(activity.getClass())) {
            throw new IllegalArgumentException("The activity " + activity.getClass().getName() +
                    " should implement OnRequestPermissionsResultCallback");
        }
        if (MyContextHolder.get().isTestRun()) {
            MyLog.i(activity, "Skipped requesting permission during a Test run: " + permissionType);
        } else {
            MyLog.i(activity, "Requesting permission: " + permissionType);
            ActivityCompat.requestPermissions(activity,
                    new String[]{permissionType.manifestPermission},
                    permissionType.ordinal());
        }
    }

    /**
     * Always returns true for "GET_ACCOUNTS", because we actually don't need this permission
     * to read accounts of this application
     */
    public static boolean checkPermission(@NonNull Context context, PermissionType permissionType) {
        return allGranted || permissionType == PermissionType.GET_ACCOUNTS ||
                ContextCompat.checkSelfPermission(context,
                permissionType.manifestPermission ) == PackageManager.PERMISSION_GRANTED;
    }

}
