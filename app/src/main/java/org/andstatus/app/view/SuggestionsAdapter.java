/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import org.andstatus.app.R;
import org.andstatus.app.SearchObjects;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * For now Timeline suggestions are taken from Search timelines, user suggestions are not persistent
 */
public class SuggestionsAdapter extends BaseAdapter implements Filterable {
    private static final List<String> notesSuggestions = new CopyOnWriteArrayList<>();
    private static final List<String> actorsSuggestions = new CopyOnWriteArrayList<>();
    private final LayoutInflater mInflater;

    private final SearchObjects searchObjects;
    private ArrayFilter mFilter;
    private List<String> items = new ArrayList<>();

    public SuggestionsAdapter(@NonNull LoadableListActivity activity, @NonNull SearchObjects searchObjects) {
        mInflater = LayoutInflater.from(activity);
        this.searchObjects = searchObjects;
        for (Timeline timeline : activity.getMyContext().timelines().values()) {
            if (timeline.hasSearchQuery() && !notesSuggestions.contains(timeline.getSearchQuery())) {
                notesSuggestions.add(timeline.getSearchQuery());
            }
        }
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public @Nullable
    String getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public @NonNull View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        final View view;
        if (convertView == null) {
            view = mInflater.inflate(R.layout.suggestion_item, parent, false);
        } else {
            view = convertView;
        }
        final String item = getItem(position);
        MyUrlSpan.showText(view, R.id.suggestion, item, false, true);
        return view;
    }

    @Override
    public @NonNull Filter getFilter() {
        if (mFilter == null) {
            mFilter = new ArrayFilter();
        }
        return mFilter;
    }

    /**
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class ArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            List<String> filteredValues = new ArrayList<>();
            if (!TextUtils.isEmpty(prefix)) {
                final String prefixString = prefix.toString().toLowerCase();
                filteredValues = loadFiltered(prefixString);
                CollectionsUtil.sort(filteredValues);
            }
            final FilterResults results = new FilterResults();
            results.values = filteredValues;
            results.count = filteredValues.size();
            return results;
        }

        private List<String> loadFiltered(final String prefixString) {
            if (StringUtil.isEmpty(prefixString)) {
                return Collections.emptyList();
            }
            List<String> filteredValues = new ArrayList<>();
            for (String item : getAllSuggestions(searchObjects)) {
                if (item.toLowerCase().startsWith(prefixString)) {
                    filteredValues.add(item);
                }
            }
            if (MyPreferences.isShowDebuggingInfoInUi()) {
                filteredValues.add("prefix:" + prefixString);
                filteredValues.add("inAllSuggestions:" + getAllSuggestions(searchObjects).size() + "items");
            }
            return filteredValues;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            //noinspection unchecked
            items = (List<String>) results.values;
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            if (resultValue == null) {
                return "(null)";
            }
            return (String) resultValue;
        }
    }

    public static void addSuggestion(SearchObjects searchObjects, String suggestion) {
        if (StringUtil.isEmpty(suggestion)) {
            return;
        }
        List<String> suggestions = getAllSuggestions(searchObjects);
        if (suggestions.contains(suggestion)) {
            suggestions.remove(suggestion);
        }
        suggestions.add(0, suggestion);
    }

    @NonNull
    private static List<String> getAllSuggestions(SearchObjects searchObjects) {
        return SearchObjects.NOTES.equals(searchObjects) ? notesSuggestions : actorsSuggestions;
    }
}
