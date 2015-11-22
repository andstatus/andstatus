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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;

import java.util.List;

class UserListViewAdapter extends BaseAdapter {
    private final Context context;
    private final int listItemLayoutId;
    private final List<UserListViewItem> oUsers;
    private final boolean showAvatars;

    public UserListViewAdapter(Context context, int listItemLayoutId, List<UserListViewItem> oUsers) {
        this.context = context;
        this.listItemLayoutId = listItemLayoutId;
        this.oUsers = oUsers;
        showAvatars = MyPreferences.showAvatars();
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
        MyUrlSpan.showText(view, R.id.username, item.mUserName + " ("
                + (TextUtils.isEmpty(item.mRealName) ? " ? " : item.mRealName) + ")", false);
        if (showAvatars) {
            showAvatar(item, view);
        }
        MyUrlSpan.showText(view, R.id.homepage, item.mHomepage, true);
        MyUrlSpan.showText(view, R.id.description, item.mDescription, false);
        MyUrlSpan.showText(view, R.id.uri, item.mUri.toString(), true);
        return view;
    }

    private View newView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (!Activity.class.isAssignableFrom(context.getClass())) {
            MyLog.w(this, "Context should be from an Activity");
        }
        return inflater.inflate(listItemLayoutId, null);
    }

    private void showAvatar(UserListViewItem item, View view) {
        ImageView avatar = (ImageView) view.findViewById(R.id.avatar_image);
        avatar.setImageDrawable(item.getAvatar());
    }
}
