/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;

public class MyActionBar {
    private final MyActionBarContainer container;
    private final int actionBarResourceId;

    public MyActionBar(MyActionBarContainer container) {
        this(container, R.layout.empty_actions);
    }

    public MyActionBar(MyActionBarContainer container, int actionBarResourceId) {
        this.container = container;
        this.actionBarResourceId = actionBarResourceId;
        if (android.os.Build.VERSION.SDK_INT >= 16 ) {
            container.getActivity().requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        MyPreferences.loadTheme(container.getActivity(), container.getActivity());
    }

    public void attach() {
        attachToView();
        setDefaultTitle();
        attachUpNavigationListener();
        setupOverflowButton();
    }

    private void attachToView() {
        LayoutInflater inflater = LayoutInflater.from(container.getActivity());
        ViewGroup actionsView = (ViewGroup) inflater.inflate(actionBarResourceId, null);
        if (!attachToKnownView(actionsView) && !attachToLinearLayout(actionsView)
                && !attachToListView(actionsView)) {
            MyLog.d(this, "Couldn't attach Action Bar");
        }
    }
    
    private boolean attachToKnownView(ViewGroup actionsView) {
        ViewGroup viewGroup = (ViewGroup) container.getActivity().findViewById(R.id.myLayoutParent);
        if (viewGroup != null) {
            viewGroup.addView(actionsView, 0);
            return true;
        }        
        return false;
    }

    private boolean attachToLinearLayout(ViewGroup actionsView) {
        ViewGroup viewGroup = findChildViewGroupOfTheClass(LinearLayout.class, (ViewGroup) container.getActivity().findViewById(android.R.id.content), 2);
        if (LinearLayout.class.isAssignableFrom(viewGroup.getClass())) {
            viewGroup.addView(actionsView, 0);
            return true;
        }
        return false;
    }

    /**
     * @return Tries to return a child of the viewClassToFind but may return any ViewGroup if not found
     */
    private ViewGroup findChildViewGroupOfTheClass(Class<? extends ViewGroup> viewClassToFind, ViewGroup parentViewGroup, int minLevelsToCheck) {
        ViewGroup viewFound = parentViewGroup;
        for (int index = 0; index < parentViewGroup.getChildCount(); index++) {
            View view = parentViewGroup.getChildAt(index);
            if (viewClassToFind.isAssignableFrom(view.getClass())) {
                viewFound = (ViewGroup) view;
                break;
            }
        }
        if ((minLevelsToCheck > 0) || !(viewClassToFind.isAssignableFrom(viewFound.getClass()))) {
            for (int index = 0; index < parentViewGroup.getChildCount(); index++) {
                View view = parentViewGroup.getChildAt(index);
                if (ViewGroup.class.isAssignableFrom(view.getClass())) {
                    ViewGroup viewGroup = findChildViewGroupOfTheClass(viewClassToFind, (ViewGroup) view, minLevelsToCheck-1);
                    if (viewClassToFind.isAssignableFrom(viewGroup.getClass())) {
                        viewFound = viewGroup;
                        break;
                    }
                }
            }
        }
        return viewFound;
    }
    
    private boolean attachToListView(ViewGroup actionsView) {
        if (android.os.Build.VERSION.SDK_INT < 16) {
            return false;
        }
        ViewGroup viewGroup = findChildViewGroupOfTheClass(ListView.class, (ViewGroup) container.getActivity().findViewById(android.R.id.content), 2);
        if (ListView.class.isAssignableFrom(viewGroup.getClass())) {
            if (PreferenceActivity.class.isAssignableFrom(container.getActivity().getClass())) {
                // Ugly but better than nothing
                ((ListView) viewGroup).addFooterView(actionsView);
            } else {
                ((ListView) viewGroup).addHeaderView(actionsView);
            }
        } else {
            viewGroup.addView(actionsView, 0);
        }
        return true;
    }

    /** http://stackoverflow.com/questions/1457803/how-do-i-access-androidlabel-for-an-activity */
    private void setDefaultTitle() {
        PackageManager pm = container.getActivity().getPackageManager();
        try {
            ActivityInfo info = pm.getActivityInfo(new ComponentName(container.getActivity(), container.getActivity().getClass()), 0);
            setTitle(info.labelRes);
        } catch (NameNotFoundException e) {
            MyLog.d(this, "setDefaultTitle", e);
        }
    }

    private void attachUpNavigationListener() {
        OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                container.closeAndGoBack();
            }
        };
        attachListenerToView(listener, R.id.upNavigation);
    }
    
    private void attachListenerToView(OnClickListener listener, int viewResourceId) {
        View view = container.getActivity().findViewById(viewResourceId);
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    private void setupOverflowButton() {
        View overflowButton = container.getActivity().findViewById(R.id.button_overflow);
        if (overflowButton == null) {
            return;
        }
        if (!container.hasOptionsMenu()) {
            overflowButton.setVisibility(View.GONE);
            return;
        }
        
        OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                container.getActivity().openOptionsMenu();
            }
        };
        attachListenerToView(listener, R.id.button_overflow);
    }
    
    public void setTitle(int resId) {
        if (resId != 0) {
            setTitle(container.getActivity().getText(resId).toString());
        }
    }

    public void setTitle(String titleText) {
        TextView titleView = (TextView) container.getActivity().findViewById(R.id.titleText);
        if (titleView != null) {
            titleView.setText(titleText);
        } else {
            String appName = container.getActivity().getText(R.string.app_name).toString();
            container.getActivity().setTitle(appName + ": " + titleText);
        }
    }
}
