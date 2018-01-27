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

package org.andstatus.app.view;

import android.support.annotation.NonNull;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.View;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.timeline.EmptyViewItem;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyContextMenu implements View.OnCreateContextMenuListener {
    public static final int MENU_GROUP_ACTOR = Menu.FIRST;
    public static final int MENU_GROUP_NOTE = Menu.FIRST + 1;
    public static final int MENU_GROUP_OBJACTOR = Menu.FIRST + 2;

    @NonNull
    protected final LoadableListActivity listActivity;
    protected final int menuGroup;
    private View viewOfTheContext = null;
    protected ViewItem mViewItem = EmptyViewItem.EMPTY;
    /**
     *  Corresponding account information ( "Reply As..." ... )
     *  oh whose behalf we are going to execute an action on this line in the list (on a note / other actor...)
     */
    @NonNull
    private MyAccount myActor = MyAccount.EMPTY;

    public MyContextMenu(@NonNull LoadableListActivity listActivity, int menuGroup) {
        this.listActivity = listActivity;
        this.menuGroup = menuGroup;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        saveContextOfSelectedItem(v);
    }

    private void saveContextOfSelectedItem(View v) {
        viewOfTheContext = v;
        ViewItem viewItem = listActivity.saveContextOfSelectedItem(v);
        if (viewItem.isEmpty() || mViewItem.isEmpty() || mViewItem.getId() != viewItem.getId()) {
            myActor = MyAccount.EMPTY;
        }
        mViewItem = viewItem;
    }

    public LoadableListActivity getActivity() {
        return listActivity;
    }

    public void showContextMenu() {
        if (viewOfTheContext != null && viewOfTheContext.getParent() != null && listActivity.isMyResumed()) {
            viewOfTheContext.post(new Runnable() {

                @Override
                public void run() {
                    try {
                        listActivity.openContextMenu(viewOfTheContext);
                    } catch (NullPointerException e) {
                        MyLog.d(this, "on showContextMenu", e);
                    }
                }
            });
        }
    }

    @NonNull
    public MyAccount getMyActor() {
        return myActor;
    }

    public void setMyActor(@NonNull MyAccount myAccount) {
        if (myAccount == null) {
            throw new IllegalArgumentException("MyAccount is null here");
        }
        this.myActor = myAccount;
    }

    public MyContext getMyContext() {
        return getActivity().getMyContext();
    }
}
