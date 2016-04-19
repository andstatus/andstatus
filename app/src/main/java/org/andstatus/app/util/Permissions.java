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
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * @author yvolk@yurivolkov.com
 * See http://developer.android.com/training/permissions/index.html
 */
public class Permissions {
    public enum PermissionType {
        READ_EXTERNAL_STORAGE(Manifest.permission.READ_EXTERNAL_STORAGE),
        WRITE_EXTERNAL_STORAGE(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        final String manifestPermission;
        PermissionType(String manifestPermission) {
            this.manifestPermission = manifestPermission;
        }
    }

    private Permissions() {
        // Empty
    }

    public static void checkAndRequest(Activity activity, PermissionType permissionType) {
        int permissionCheck = ContextCompat.checkSelfPermission(activity, permissionType.manifestPermission );
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{permissionType.manifestPermission},
                    permissionType.ordinal());
        }
    }
}
