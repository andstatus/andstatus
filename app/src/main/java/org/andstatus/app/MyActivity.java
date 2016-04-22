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

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;

import org.andstatus.app.context.MyTheme;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

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
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_action_bar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
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

    public void setTitle(String title) {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(title);
        }
    }

    public void setSubtitle(CharSequence subtitle) {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setSubtitle(subtitle);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        toggleFullscreen(TriState.FALSE);
    }

    /**
     * Sets or Toggles fullscreen mode
     * REQUIRE: android:configChanges="orientation|screenSize"
     * Based on http://stackoverflow.com/a/30224178/297710
     * On Immersive mode: https://developer.android.com/training/system-ui/immersive.html
     */
    public void toggleFullscreen(TriState fullScreenIn) {
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int uiOptionsNew = uiOptions;
        boolean fullscreenNew = ((uiOptions & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            fullscreenNew = actionBar.isShowing();
        }
        fullscreenNew = fullScreenIn.toBoolean(fullscreenNew);

        if (actionBar != null) {
            if (fullscreenNew) {
                actionBar.hide();
            } else {
                actionBar.show();
            }
        }
        if (fullscreenNew) {
            if (Build.VERSION.SDK_INT >= 14) {
                uiOptionsNew |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }
            if (Build.VERSION.SDK_INT >= 16) {
                uiOptionsNew |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
            if (Build.VERSION.SDK_INT >= 19) {
                uiOptionsNew |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
        } else {
            if (Build.VERSION.SDK_INT >= 14) {
                uiOptionsNew &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }
            if (Build.VERSION.SDK_INT >= 16) {
                uiOptionsNew &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
            if (Build.VERSION.SDK_INT >= 19) {
                uiOptionsNew &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
        }
        getWindow().getDecorView().setSystemUiVisibility(uiOptionsNew);
    }
}
