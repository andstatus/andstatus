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

package org.andstatus.app.user;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserAutoCompleteAdapter extends BaseAdapter implements Filterable {
    private final Origin origin;
    private final MyActivity myActivity;
    private final LayoutInflater mInflater;

    private ArrayFilter mFilter;
    private List<UserListViewItem> items = new ArrayList<>();

    public UserAutoCompleteAdapter(@NonNull MyActivity myActivity, @NonNull Origin origin) {
        this.origin = origin;
        this.myActivity =myActivity;
        mInflater = LayoutInflater.from(myActivity);
    }

    public Origin getOrigin() {
        return origin;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public @Nullable UserListViewItem getItem(int position) {
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
            view = mInflater.inflate(R.layout.user_lookup, parent, false);
        } else {
            view = convertView;
        }
        final UserListViewItem item = getItem(position);
        String userName = item == null ? "???" : item.getWebFingerIdOrUserName();
        MyUrlSpan.showText(view, R.id.username, userName, false, true);
        MyUrlSpan.showText(view, R.id.description, item == null ? "" :
                I18n.trimTextAt(item.mbUser.getDescription(), 80).toString(), false, false);
        showAvatar(view, item);
        return view;
    }

    private void showAvatar(View view, UserListViewItem item) {
        AvatarView avatarView = (AvatarView) view.findViewById(R.id.avatar_image);
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

    /**
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class ArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            List<UserListViewItem> filteredValues = new ArrayList<>();
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

        private List<UserListViewItem> loadFiltered(final String prefixString) {
            if (!origin.isValid()) {
                return Collections.emptyList();
            }
            UserListLoader loader = new UserListLoader(UserListType.USERS,
                    MyContextHolder.get().persistentAccounts().getFirstSucceededForOrigin(origin), origin, 0, "") {
                @NonNull
                @Override
                protected String getSelection() {
                    return UserTable.TABLE_NAME + "." + UserTable.ORIGIN_ID + "=" + origin.getId() + " AND "
                            + UserTable.TABLE_NAME + "." + UserTable.WEBFINGER_ID + " LIKE '" + prefixString + "%'";
                }
            };
            loader.load(null);
            List<UserListViewItem> filteredValues = loader.getList();
            for (UserListViewItem viewItem : filteredValues) {
                MyLog.v(this, "filtered: " + viewItem.mbUser);
            }
            return filteredValues;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            //noinspection unchecked
            items = (List<UserListViewItem>) results.values;
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
            return origin.isMentionAsWebFingerId() ? ((UserListViewItem)resultValue).getWebFingerIdOrUserName()
                    : ((UserListViewItem)resultValue).mbUser.getUserName();
        }
    }
}
