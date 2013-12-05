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

import android.text.TextUtils;

/**
 * Add selection and it's argument (for query...)
 */
public final class SelectionAndArgs {
  public String selection;
  public String[] selectionArgs;
  public int nArgs;
  
  public SelectionAndArgs() {
      clear();
  }
  
  public void clear() {
      selection = "";
      selectionArgs = new String[] {};
      nArgs = 0;
  }
  
  public int addSelection(String selectionAdd, String[] selectionArgsAdd) {
      if (selectionArgsAdd == null) {
          selectionArgsAdd = new String[] {};
      }
      int nArgsAdd = selectionArgsAdd.length;
      if (!TextUtils.isEmpty(selectionAdd)) {
          if (selection.length() == 0) {
              selection = selectionAdd;
          } else {
              selection = "(" + selection + ") AND (" + selectionAdd + ")";
          }
      }
      if (nArgsAdd > 0) {
          String[] selectionArgs2 = new String[nArgs + nArgsAdd];
          System.arraycopy(selectionArgs, 0, selectionArgs2, 0, nArgs);
          System.arraycopy(selectionArgsAdd, 0, selectionArgs2, nArgs, nArgsAdd);
          selectionArgs = selectionArgs2;
          
          nArgs += nArgsAdd;
      }
      return nArgs;
  }
}
