/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.widget;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.CollapsibleActionView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.Spinner;

import org.andstatus.app.ClassInApplicationPackage;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.R;
import org.andstatus.app.SearchObjects;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.user.UserListType;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class MySearchView extends LinearLayout implements CollapsibleActionView, SearchView.OnQueryTextListener {
    LoadableListActivity parentActivity = null;
    Timeline timeline = Timeline.getEmpty(MyAccount.EMPTY);
    SearchView searchView = null;
    Spinner searchObjects = null;
    CheckBox globalSearch = null;
    CheckBox combined = null;

    public MySearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(@NonNull LoadableListActivity loadableListActivity) {
        this.parentActivity = loadableListActivity;
        searchView = (SearchView) findViewById(R.id.search_view);
        if (searchView == null) {
            MyLog.e(this, "searchView is null");
            return;
        }
        searchObjects = (Spinner) findViewById(R.id.search_objects);
        if (searchObjects == null) {
            MyLog.e(this, "searchObjects is null");
            return;
        }
        searchObjects.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setSearchableInfo();
                setAppSearchData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        searchView.setIconifiedByDefault(false);
        searchView.setOnQueryTextListener(this);

        View upButton = findViewById(R.id.upButton);
        if (upButton == null) {
            MyLog.e(this, "upButton is null");
            return;
        }
        upButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onActionViewCollapsed();
            }
        });

        globalSearch = (CheckBox) findViewById(R.id.global_search);
        if (globalSearch == null) {
            MyLog.e(this, "globalSearch is null");
            return;
        }
        globalSearch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setAppSearchData();
            }
        });

        combined = (CheckBox) findViewById(R.id.combined);
        if (combined == null) {
            MyLog.e(this, "combined is null");
            return;
        }
        combined.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setAppSearchData();
            }
        });

        onActionViewCollapsed();
        setSearchableInfo();
        setAppSearchData();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (isInternetSearch()) {
            launchInternetSearch(query);
        }
        onActionViewCollapsed();
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public void onActionViewExpanded() {
        searchView.onActionViewExpanded();
        setVisibility(VISIBLE);
        parentActivity.hideActionBar(true);
    }

    @Override
    public void onActionViewCollapsed() {
        searchView.onActionViewCollapsed();
        setVisibility(GONE);
        parentActivity.hideActionBar(false);
    }

    private void launchInternetSearch(String query) {
        for (Origin origin : parentActivity.getMyContext().persistentOrigins().originsForInternetSearch(
                getSearchObjects(), getOrigin(), isCombined())) {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newSearch(getSearchObjects(), parentActivity.getMyContext(), origin, query));
        }
    }

    public void startSearch(@NonNull Timeline timeline) {
        this.timeline = timeline;
        if (isCombined() != timeline.isCombined()) {
            combined.setChecked(timeline.isCombined());
        }

        setAppSearchData();
        onActionViewExpanded();
    }

    private void setSearchableInfo() {
        SearchManager searchManager = (SearchManager) parentActivity.getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(
                new ComponentName(ClassInApplicationPackage.PACKAGE_NAME,
                        getSearchObjects().getActivityClass().getName())));
    }

    private void setAppSearchData() {
        final String method = "setAppSearchData";
        if (searchView == null || globalSearch == null) {
            return;
        }
        Bundle appSearchData = new Bundle();
        if (getSearchObjects() == SearchObjects.MESSAGES) {
            appSearchData.putString(IntentExtra.MATCHED_URI.key,
                    MatchedUri.getTimelineUri(
                            timeline.fromSearch(parentActivity.getMyContext(), isInternetSearch())
                            .fromIsCombined(parentActivity.getMyContext(), isCombined())
                    ).toString());
        } else {
            appSearchData.putString(IntentExtra.MATCHED_URI.key,
                    MatchedUri.getUserListUri(parentActivity.getCurrentMyAccount().getUserId(), UserListType.USERS,
                            isCombined() ? 0 : getOrigin().getId(),
                            0, "").toString());
        }
        appSearchData.putBoolean(IntentExtra.GLOBAL_SEARCH.key, isInternetSearch());
        MyLog.v(this, method + ": " + appSearchData);

        // Calling hidden method using reflection, see https://stackoverflow.com/a/161005/297710
        try {
            searchView.getClass().getMethod(method, Bundle.class).invoke(searchView, appSearchData);
        } catch (Exception e) {
            MyLog.e(this, e);
        }
    }

    SearchObjects getSearchObjects() {
        return SearchObjects.fromSpinner(searchObjects);
    }

    private Origin getOrigin() {
        return timeline.getOrigin().isValid() ? timeline.getOrigin() : parentActivity.getCurrentMyAccount().getOrigin();
    }

    private boolean isInternetSearch() {
        if (globalSearch == null) {
            return false;
        }
        boolean isInternetSearchEnabled = parentActivity.getMyContext()
                .persistentOrigins().isSearchSupported(
                        getSearchObjects(), getOrigin(), isCombined());
        globalSearch.setEnabled(isInternetSearchEnabled);
        if (!isInternetSearchEnabled && globalSearch.isChecked()) {
            globalSearch.setChecked(false);
        }
        return isInternetSearchEnabled && globalSearch.isChecked();
    }

    private boolean isCombined() {
        return combined != null && combined.isChecked();
    }
}
