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
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v4.util.Pair;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.woxthebox.draglistview.DragItem;
import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyResources;

import java.util.ArrayList;

/**
 * @author yvolk@yurivolkov.com
 */
public class AccountListFragment extends Fragment {

    private ArrayList<Pair<Long, String>> mItemArray;
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
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.drag_list_layout, container, false);
        mDragListView = (DragListView) view.findViewById(R.id.drag_list_view);
        mDragListView.getRecyclerView().setVerticalScrollBarEnabled(true);
        mDragListView.setDragListListener(new DragListView.DragListListenerAdapter() {
            @Override
            public void onItemDragStarted(int position) {
                Toast.makeText(mDragListView.getContext(), "Start - position: " + position, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onItemDragEnded(int fromPosition, int toPosition) {
                if (fromPosition != toPosition) {
                    Toast.makeText(mDragListView.getContext(), "End - position: " + toPosition, Toast.LENGTH_SHORT).show();
                }
            }
        });

        mItemArray = new ArrayList<>();
        for (MyAccount myAccount : MyContextHolder.get().persistentAccounts().list()) {
            mItemArray.add(new Pair<>(myAccount.getUserId(), myAccount.getAccountName()));
        }

        setupListRecyclerView();
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ((MyActivity) getActivity()).getSupportActionBar().setTitle(R.string.header_manage_existing_accounts);
    }

    private void setupListRecyclerView() {
        mDragListView.setLayoutManager(new LinearLayoutManager(getContext()));
        ItemAdapter listAdapter = new ItemAdapter(mItemArray, R.layout.drag_accountlist_item, R.id.dragHandle, false);
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
                            R.attr.actionBarColorAccent, dragView.getContext().getTheme()));
        }
    }

    class ItemAdapter extends DragItemAdapter<Pair<Long, String>, ItemAdapter.ViewHolder> {

        private int mLayoutId;
        private int mGrabHandleId;

        public ItemAdapter(ArrayList<Pair<Long, String>> list, int layoutId, int grabHandleId, boolean dragOnLongPress) {
            super(dragOnLongPress);
            mLayoutId = layoutId;
            mGrabHandleId = grabHandleId;
            setHasStableIds(true);
            setItemList(list);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            String text = mItemList.get(position).second;
            holder.mText.setText(text);
            holder.itemView.setTag(text);
        }

        @Override
        public long getItemId(int position) {
            return mItemList.get(position).first;
        }

        public class ViewHolder extends DragItemAdapter<Pair<Long, String>, ItemAdapter.ViewHolder>.ViewHolder {
            public TextView mText;

            public ViewHolder(final View itemView) {
                super(itemView, mGrabHandleId);
                mText = (TextView) itemView.findViewById(R.id.visible_name);
            }

            @Override
            public void onItemClicked(View view) {
                Toast.makeText(view.getContext(), "Item " + mItemId + " clicked", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getActivity(), AccountSettingsActivity.class);
                intent.putExtra(IntentExtra.ACCOUNT_NAME.key,
                        MyContextHolder.get().persistentAccounts().fromUserId(mItemId).getAccountName());
                getActivity().startActivity(intent);
            }

            @Override
            public boolean onItemLongClicked(View view) {
                Toast.makeText(view.getContext(), "Item " + mItemId + " long clicked", Toast.LENGTH_SHORT).show();
                return true;
            }
        }
    }
}
