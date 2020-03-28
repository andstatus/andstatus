/*
 * Copyright (C) 2017-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.actor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.andstatus.app.R;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.note.NoteBodyTokenizer;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.StringUtil;

import java.util.Collections;
import java.util.List;

public class ActorAutoCompleteAdapter extends BaseAdapter implements Filterable {
    private final Origin origin;
    private final LoadableListActivity myActivity;
    private final LayoutInflater mInflater;

    private ArrayFilter mFilter;
    private FilteredValues items = FilteredValues.EMPTY;

    public ActorAutoCompleteAdapter(@NonNull LoadableListActivity myActivity, @NonNull Origin origin) {
        this.origin = origin;
        this.myActivity =myActivity;
        mInflater = LayoutInflater.from(myActivity);
    }

    public Origin getOrigin() {
        return origin;
    }

    @Override
    public int getCount() {
        return items.viewItems.size();
    }

    @Override
    public @Nullable
    ActorViewItem getItem(int position) {
        return items.viewItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public @NonNull View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        final View view;
        if (convertView == null) {
            view = mInflater.inflate(R.layout.actor_lookup, parent, false);
        } else {
            view = convertView;
        }
        final ActorViewItem item = getItem(position);
        if (item == null || item.actor.isEmpty()) {
            MyUrlSpan.showText(view, R.id.username,
                    myActivity.getText(R.string.nothing_in_the_loadable_list).toString(),false,true);
        } else {
            String username = item.getActor().getUniqueName();
            MyUrlSpan.showText(view, R.id.username, username, false, true);
            MyUrlSpan.showText(view, R.id.description,
                    I18n.trimTextAt(item.actor.getSummary(), 80).toString(), false, false);
            showAvatar(view, item);
        }
        return view;
    }

    private void showAvatar(View view, ActorViewItem item) {
        AvatarView avatarView = view.findViewById(R.id.avatar_image);
        if (item == null) {
            avatarView.setVisibility(View.INVISIBLE);
        } else {
            item.showAvatar(myActivity, avatarView);
        }
    }

    @Override
    public @NonNull Filter getFilter() {
        if (mFilter == null) {
            mFilter = new ArrayFilter();
        }
        return mFilter;
    }

    private static class FilteredValues {
        final static FilteredValues EMPTY = new FilteredValues(false, "", Collections.emptyList());

        final boolean matchGroupsOnly;
        final String referenceChar;
        final List<ActorViewItem> viewItems;

        private FilteredValues(boolean matchGroupsOnly, String referenceChar, List<ActorViewItem> viewItems) {
            this.matchGroupsOnly = matchGroupsOnly;
            this.referenceChar = referenceChar;
            this.viewItems = viewItems;
        }
    }

    /**
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class ArrayFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence prefixWithReferenceChar) {
            if (!origin.isValid() || StringUtil.isEmpty(prefixWithReferenceChar) ||
                    prefixWithReferenceChar.length() < NoteBodyTokenizer.MIN_LENGHT_TO_SEARCH + 1) {
                final FilterResults results = new FilterResults();
                results.values = FilteredValues.EMPTY;
                results.count = 0;
                return results;
            }
            char referenceChar = prefixWithReferenceChar.charAt(0);
            String prefixString = prefixWithReferenceChar.toString().substring(1);
            boolean matchGroupsOnly = origin.groupActorReferenceChar().map(c -> c == referenceChar).orElse(false);

            List<ActorViewItem> viewItems = loadFiltered(matchGroupsOnly, prefixString.toLowerCase());
            CollectionsUtil.sort(viewItems);

            final FilterResults results = new FilterResults();
            results.values = new FilteredValues(matchGroupsOnly, String.valueOf(referenceChar), viewItems);
            results.count = viewItems.size();
            return results;
        }

        private List<ActorViewItem> loadFiltered(boolean matchGroupsOnly, String prefixString) {
            ActorListLoader loader = new ActorListLoader(myActivity.getMyContext(), ActorListType.ACTORS_AT_ORIGIN,
                    origin, 0, "") {
                @NonNull
                @Override
                protected String getSelection() {
                    if (matchGroupsOnly) {
                        return ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.getId() + " AND " +
                                ActorTable.TABLE_NAME + "." + ActorTable.GROUP_TYPE +
                                " IN (" + GroupType.GENERIC.id + ", " + GroupType.ACTOR_OWNED.id + ") AND " +
                                ActorTable.TABLE_NAME + "." + ActorTable.USERNAME + " LIKE '" + prefixString + "%'";
                    } else {
                        return ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.getId() + " AND "
                                + ActorTable.TABLE_NAME + "." + ActorTable.WEBFINGER_ID + " LIKE '" + prefixString + "%'";
                    }
                }
            };
            loader.load(null);
            List<ActorViewItem> filteredValues = loader.getList();
            for (ActorViewItem viewItem : filteredValues) {
                MyLog.v(this, () -> "filtered: " + viewItem.actor);
            }
            return filteredValues;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            //noinspection
            items = (FilteredValues) results.values;
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            if (!(resultValue instanceof ActorViewItem)) {
                return "";
            }
            ActorViewItem item = (ActorViewItem) resultValue;
            return items.referenceChar +
                (item.getActor().isEmpty()
                    ? ""
                    : origin.isMentionAsWebFingerId() && !items.matchGroupsOnly
                        ? item.actor.getUniqueName()
                        : item.actor.getUsername()
                );
        }
    }
}
