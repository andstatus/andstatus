/*
 * Copyright (C) 2011 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.Arrays;

/**
 * Add selection and it's argument (for query...)
 */
public final class SelectionAndArgs implements TaggedClass {
    private static final String TAG = SelectionAndArgs.class.getSimpleName();
    public volatile String selection;
    public volatile String[] selectionArgs;
    private volatile int nArgs;

    public SelectionAndArgs() {
        this("");
    }

    public SelectionAndArgs(String selection_in) {
        clear();
        selection = selection_in;
    }

    public void clear() {
        selection = "";
        selectionArgs = new String[] {};
        nArgs = 0;
    }

    public int addSelection(String selection_in) {
        return addSelection(selection_in, new String[] {});
    }

    public int addSelection(String selectionAdd, String selectionArgAdd) {
        return addSelection(selectionAdd, new String[] { selectionArgAdd});
    }

    public int addSelection(String selectionAdd, String[] selectionArgsAdd) {
        int nArgsAdd = selectionArgsAdd == null ? 0 : selectionArgsAdd.length;
        if (!StringUtil.isEmpty(selectionAdd)) {
            if (selection.length() == 0) {
                selection = selectionAdd;
            } else {
                selection = "(" + selection + ") AND (" + selectionAdd + ")";
            }
        }
        if (nArgsAdd > 0) {
            int nArgs2 = nArgs + nArgsAdd;
            String[] selectionArgs2 = new String[nArgs2];
            System.arraycopy(selectionArgs, 0, selectionArgs2, 0, nArgs);
            System.arraycopy(selectionArgsAdd, 0, selectionArgs2, nArgs, nArgsAdd);
            selectionArgs = selectionArgs2;
            nArgs = nArgs2;
        }
        return nArgs;
    }

    public boolean isEmpty() {
        return nArgs == 0;
    }

    @Override
    public String toString() {
        return MyStringBuilder.formatKeyValue(this, selection + ", args:" + Arrays.toString(selectionArgs));
    }

    @Override
    public String classTag() {
        return TAG;
    }
}
