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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.CollapsibleActionView;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.SearchObjects;
import org.andstatus.app.actor.ActorsScreenType;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.view.SuggestionsAdapter;

/**
 * @author yvolk@yurivolkov.com
 */
public class MySearchView extends LinearLayout implements CollapsibleActionView{
    LoadableListActivity parentActivity = null;
    Timeline timeline = isInEditMode() ? null : Timeline.EMPTY;
    AutoCompleteTextView searchText = null;
    Spinner searchObjects = null;
    CheckBox internetSearch = null;
    CheckBox combined = null;
    private boolean isInternetSearchEnabled;

    public MySearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(@NonNull LoadableListActivity loadableListActivity) {
        this.parentActivity = loadableListActivity;
        searchText = (AutoCompleteTextView) findViewById(R.id.search_text);
        if (searchText == null) {
            MyLog.w(this, "searchView is null");
            return;
        }
        searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            // See https://stackoverflow.com/questions/3205339/android-how-to-make-keyboard-enter-button-say-search-and-handle-its-click
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    String query = v.getText().toString();
                    if (StringUtil.nonEmpty(query)) {
                        onQueryTextSubmit(query);
                        return true;
                    }
                }
                return false;
            }
        });

        searchObjects = (Spinner) findViewById(R.id.search_objects);
        if (searchObjects == null) {
            MyLog.w(this, "searchObjects is null");
            return;
        }
        searchObjects.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onSearchObjectsChange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        View upButton = findViewById(R.id.up_button);
        if (upButton == null) {
            MyLog.w(this, "upButton is null");
            return;
        }
        upButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onActionViewCollapsed();
            }
        });

        internetSearch = (CheckBox) findViewById(R.id.internet_search);
        if (internetSearch == null) {
            MyLog.w(this, "internetSearch is null");
            return;
        }

        combined = (CheckBox) findViewById(R.id.combined);
        if (combined == null) {
            MyLog.w(this, "combined is null");
            return;
        }
        combined.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onSearchContextChanged();
            }
        });

        View submitButton = findViewById(R.id.submit_button);
        if (submitButton == null) {
            MyLog.w(this, "submitButton is null");
            return;
        }
        submitButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onQueryTextSubmit(searchText.getText().toString());
            }
        });

        onActionViewCollapsed();
        onSearchObjectsChange();
    }

    public void showSearchView(@NonNull Timeline timeline) {
        this.timeline = timeline;
        if (isCombined() != timeline.isCombined()) {
            combined.setChecked(timeline.isCombined());
        }
        onActionViewExpanded();
    }

    @Override
    public void onActionViewExpanded() {
        onSearchObjectsChange();
        setVisibility(VISIBLE);
        parentActivity.hideActionBar(true);
    }

    @Override
    public void onActionViewCollapsed() {
        setVisibility(GONE);
        parentActivity.hideActionBar(false);
        searchText.setText("");
    }

    public void onQueryTextSubmit(String query) {
        MyLog.d(this, "Submitting " + getSearchObjects() + " query '" + query + "'"
                + (isInternetSearch() ? " Internet" : "")
                + (isCombined() ? " combined" : "")
        );
        if (isInternetSearch()) {
            launchInternetSearch(query);
        }
        launchActivity(query);
        onActionViewCollapsed();
        searchText.setAdapter(null);
        SuggestionsAdapter.addSuggestion(getSearchObjects(), query);
    }

    private void launchInternetSearch(String query) {
        for (Origin origin : parentActivity.getMyContext().origins().originsForInternetSearch(
                getSearchObjects(), getOrigin(), isCombined())) {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newSearch(getSearchObjects(), parentActivity.getMyContext(), origin, query));
        }
    }

    private void onSearchObjectsChange() {
        searchText.setAdapter(new SuggestionsAdapter(parentActivity, getSearchObjects()));
        searchText.setHint(getSearchObjects() == SearchObjects.NOTES ? R.string.search_timeline_hint
                : R.string.search_userlist_hint );
        onSearchContextChanged();
    }


    private void launchActivity(String query) {
        if (StringUtil.isEmpty(query)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEARCH, getUri(), getContext(), getSearchObjects().getActivityClass());
        intent.putExtra(IntentExtra.SEARCH_QUERY.key, query);
        if (timeline.hasSearchQuery()
                && getSearchObjects() == SearchObjects.NOTES
                && !parentActivity.getMyContext().timelines().getDefault().equals(timeline)) {
            // Send intent to existing activity
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        getContext().startActivity(intent);
    }

    private void onSearchContextChanged() {
        isInternetSearchEnabled = parentActivity.getMyContext()
                .origins().isSearchSupported(
                        getSearchObjects(), getOrigin(), isCombined());
        internetSearch.setEnabled(isInternetSearchEnabled);
        if (!isInternetSearchEnabled && internetSearch.isChecked()) {
            internetSearch.setChecked(false);
        }
    }

    private Uri getUri() {
        switch (getSearchObjects()) {
            case NOTES:
                return timeline.fromSearch(parentActivity.getMyContext(), isInternetSearch())
                        .fromIsCombined(parentActivity.getMyContext(), isCombined()).getUri();
            case ACTORS:
                return MatchedUri.getActorsScreenUri(ActorsScreenType.ACTORS_AT_ORIGIN,
                        isCombined() ? 0 : getOrigin().getId(), 0, "");
            case GROUPS:
                return MatchedUri.getActorsScreenUri(ActorsScreenType.GROUPS_AT_ORIGIN,
                        isCombined() ? 0 : getOrigin().getId(), 0, "");
            default:
                return Uri.EMPTY;
        }
    }

    SearchObjects getSearchObjects() {
        return SearchObjects.fromSpinner(searchObjects);
    }

    private Origin getOrigin() {
        return timeline.getOrigin().isValid()
                ? timeline.getOrigin()
                : parentActivity.getMyContext().accounts().getCurrentAccount().getOrigin();
    }

    private boolean isInternetSearch() {
        return isInternetSearchEnabled && internetSearch != null && internetSearch.isChecked();
    }

    private boolean isCombined() {
        return combined != null && combined.isChecked();
    }
}
