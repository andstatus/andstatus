/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.account;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.woxthebox.draglistview.DragItem;
import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyResources;
import org.andstatus.app.util.MyUrlSpan;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yvolk@yurivolkov.com
 */
public class AccountListFragment extends Fragment {

    private List<MyAccount> mItems;
    private DragListView mDragListView;

    public static ListFragment newInstance() {
        return new ListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.drag_list_layout, container, false);
        mDragListView = view.findViewById(R.id.drag_list_view);
        mDragListView.getRecyclerView().setVerticalScrollBarEnabled(true);

        mItems = new CopyOnWriteArrayList<>(MyContextHolder.get().accounts().get());
        setupListRecyclerView();
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        MyActivity activity = (MyActivity) getActivity();
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.manage_accounts);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MyContextHolder.get().accounts().reorderAccounts(mItems);
    }

    private void setupListRecyclerView() {
        mDragListView.setLayoutManager(new LinearLayoutManager(getContext()));
        ItemAdapter listAdapter = new ItemAdapter(mItems, R.layout.drag_accountlist_item, R.id.dragHandle);
        mDragListView.setAdapter(listAdapter, true);
        mDragListView.setCanDragHorizontally(false);
        mDragListView.setCustomDragItem(new MyDragItem(getContext(), R.layout.drag_accountlist_item));
    }

    private static class MyDragItem extends DragItem {

        public MyDragItem(Context context, int layoutId) {
            super(context, layoutId);
        }

        @Override
        public void onBindDragView(View clickedView, View dragView) {
            CharSequence text = ((TextView) clickedView.findViewById(R.id.visible_name)).getText();
            ((TextView) dragView.findViewById(R.id.visible_name)).setText(text);
            dragView.setBackgroundColor(
                    MyResources.getColorByAttribute(
                            R.attr.actionBarColorAccent, android.R.attr.colorAccent, dragView.getContext().getTheme()));
        }

    }

    class ItemAdapter extends DragItemAdapter<MyAccount, ItemAdapter.ViewHolder> {

        private int mLayoutId;
        private int mGrabHandleId;

        ItemAdapter(List<MyAccount> list, int layoutId, int grabHandleId) {
            super();
            mLayoutId = layoutId;
            mGrabHandleId = grabHandleId;
            setHasStableIds(true);
            setItemList(list);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view, mGrabHandleId);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            MyAccount ma = mItemList.get(position);
            String visibleName = ma.getAccountName();
            if (!ma.isValidAndSucceeded()) {
                visibleName = "(" + visibleName + ")";
            }
            MyUrlSpan.showText(holder.itemView, R.id.visible_name, visibleName, false, true);
            MyUrlSpan.showText(holder.itemView, R.id.sync_auto,
                    ma.isSyncedAutomatically() && ma.isValidAndSucceeded() ?
                            getText(R.string.synced_abbreviated).toString() : "", false, true);
            holder.itemView.setTag(visibleName);
        }

        @Override
        public long getUniqueItemId(int position) {
            return mItemList.get(position).getActorId();
        }

        public class ViewHolder extends DragItemAdapter.ViewHolder {

            public ViewHolder(final View itemView, int grabHandleId) {
                super(itemView, grabHandleId, false);
            }

            @Override
            public void onItemClicked(View view) {
                Intent intent = new Intent(getActivity(), AccountSettingsActivity.class);
                intent.putExtra(IntentExtra.ACCOUNT_NAME.key,
                        MyContextHolder.get().accounts().fromActorId(mItemId).getAccountName());
                getActivity().startActivity(intent);
            }

            @Override
            public boolean onItemLongClicked(View view) {
                return true;
            }
        }
    }
}
