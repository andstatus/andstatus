/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;

import org.andstatus.app.context.MyTheme;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyActivity extends AppCompatActivity {

    protected int mLayoutId = 0;
    private Menu mOptionsMenu = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MyLog.v(this, "onCreate");
        MyTheme.loadTheme(this);
        super.onCreate(savedInstanceState);
        if (mLayoutId != 0) {
            MyTheme.setContentView(this, mLayoutId);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    public Menu getOptionsMenu() {
        return mOptionsMenu;
    }
}
