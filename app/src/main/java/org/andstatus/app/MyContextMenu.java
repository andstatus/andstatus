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

package org.andstatus.app;

import android.support.annotation.NonNull;
import android.view.ContextMenu;
import android.view.View;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyContextMenu implements View.OnCreateContextMenuListener {
    protected final LoadableListActivity listActivity;
    protected View viewOfTheContext = null;
    protected Object oViewItem = null;
    protected MyAccount myPotentialActor = MyAccount.getEmpty();

    public MyContextMenu(LoadableListActivity listActivity) {
        this.listActivity = listActivity;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        saveContextOfSelectedItem(v);
    }

    protected void saveContextOfSelectedItem(View v) {
        if (viewOfTheContext != v) {
            myPotentialActor = MyAccount.getEmpty();
        }
        viewOfTheContext = v;
        oViewItem = listActivity.saveContextOfSelectedItem(v);
    }

    public LoadableListActivity getActivity() {
        return listActivity;
    }

    public void showContextMenu() {
        if (viewOfTheContext != null &&  viewOfTheContext.getParent() != null) {
            viewOfTheContext.post(new Runnable() {

                @Override
                public void run() {
                    try {
                        viewOfTheContext.showContextMenu();
                    } catch (NullPointerException e) {
                        MyLog.d(this, "on showContextMenu", e);
                    }
                }
            });
        }
    }

    public MyAccount getMyPotentialActor() {
        return myPotentialActor;
    }

    public void setMyPotentialActor(@NonNull MyAccount myAccount) {
        this.myPotentialActor = myAccount;
    }

    public MyAccount getPotentialActorOrCurrentAccount() {
        return myPotentialActor.isValid() ? myPotentialActor :
                MyContextHolder.get().persistentAccounts().getCurrentAccount();
    }
}
