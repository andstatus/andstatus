/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;

import java.util.List;

public class UserListViewAdapter extends BaseAdapter {
    private final Context context;
    final int listItemLayoutId;
    private final List<UserListViewItem> oUsers;

    public UserListViewAdapter(Context context, int listItemLayoutId, List<UserListViewItem> oUsers) {
        this.context = context;
        this.listItemLayoutId = listItemLayoutId;
        this.oUsers = oUsers;
    }

    @Override
    public int getCount() {
        return oUsers.size();
    }

    @Override
    public Object getItem(int position) {
        return oUsers.get(position);
    }

    @Override
    public long getItemId(int position) {
        return oUsers.get(position).getUserId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? newView() : convertView;
        UserListViewItem item = oUsers.get(position);
        ((TextView) view.findViewById(R.id.id)).setText(Long.toString(item.getUserId()));
        setUsername(item, view);
        return view;
    }

    private void setUsername(UserListViewItem item, View view) {
        TextView userName = (TextView) view.findViewById(R.id.username);
        userName.setText(item.mUserName);
    }

    private View newView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (!Activity.class.isAssignableFrom(context.getClass())) {
            MyLog.w(this, "Context should be from an Activity");
        }
        return inflater.inflate(listItemLayoutId, null);
    }
}
