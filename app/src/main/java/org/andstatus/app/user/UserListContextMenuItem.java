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

import android.content.Intent;
import android.net.Uri;
import android.view.Menu;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.ContextMenuItem;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.msg.TimelineTypeSelector;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

public enum UserListContextMenuItem implements ContextMenuItem {
    GET_USER() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            CommandData commandData = CommandData.getUser(ma.getAccountName(),
                    menu.getViewItem().getUserId(), menu.getViewItem().mbUser.getUserName());
            MyServiceManager.sendManualForegroundCommand(commandData);
        }
    },
    DIRECT_MESSAGE() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            // TODO
        }
    },
    SHARE() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            // TODO
        }
    },
    USER_MESSAGES() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
            Intent intent = new Intent(menu.getActivity(), TimelineActivity.class);
            intent.setData(MatchedUri.getTimelineUri(ma.getUserId(), TimelineTypeSelector.selectableType(TimelineType.USER),
                    true, menu.getViewItem().getUserId()));
            menu.getActivity().startActivity(intent);
        }
    },
    FOLLOW() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            sendCommandUser(CommandEnum.FOLLOW_USER, ma, menu.getViewItem().getUserId());
        }
    },
    STOP_FOLLOWING() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            sendCommandUser(CommandEnum.STOP_FOLLOWING_USER, ma, menu.getViewItem().getUserId());
        }
    },
    ACT_AS_USER() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            menu.setAccountUserIdToActAs(ma.firstOtherAccountOfThisOrigin().getUserId());
            menu.showContextMenu();
        }
    },
    ACT_AS() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            AccountSelector.selectAccount(menu.getActivity(), ma.getOriginId(), ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS);
        }
    },
    FOLLOWERS(true) {
        private volatile MyAccount ma2 = null;
        @Override
        void executeAsync(UserListContextMenu menu, MyAccount ma) {
            ma2 = ma;
            long userId = menu.getViewItem().getUserId();
            long originId = MyQuery.userIdToLongColumnValue(MyDatabase.User.ORIGIN_ID, userId);
            if (originId == 0) {
                MyLog.e(this, "Unknown origin for userId=" + userId);
                return;
            }
            if (!ma2.isValid() || ma2.getOriginId() != originId) {
                ma2 = MyContextHolder.get().persistentAccounts().fromUserId(userId);
                if (!ma2.isValid()) {
                    ma2 = MyContextHolder.get().persistentAccounts().findFirstSucceededMyAccountByOriginId(originId);
                }
            }
        }

        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            Uri uri = MatchedUri.getUserListUri(ma2.getUserId(),
                    UserListType.FOLLOWERS, false,
                    menu.getViewItem().getUserId());
            if (MyLog.isVerboseEnabled()) {
                MyLog.d(this, "onItemClick, startActivity=" + uri);
            }
            menu.getActivity().startActivity(MyAction.VIEW_FOLLOWERS.getIntent(uri));
        }
    },
    NONEXISTENT(),
    UNKNOWN();

    private final boolean mIsAsync;
    private static final String TAG = UserListContextMenuItem.class.getSimpleName();

    UserListContextMenuItem() {
        this(false);
    }

    UserListContextMenuItem(boolean isAsync) {
        this.mIsAsync = isAsync;
    }

    @Override
    public int getId() {
        return Menu.FIRST + ordinal() + 1;
    }
    
    public static UserListContextMenuItem fromId(int id) {
        for (UserListContextMenuItem item : UserListContextMenuItem.values()) {
            if (item.getId() == id) {
                return item;
            }
        }
        return UNKNOWN;
    }

    public void addTo(Menu menu, int order, int titleRes) {
        menu.add(Menu.NONE, this.getId(), order, titleRes);
    }

    public void addTo(Menu menu, int order, CharSequence title) {
        menu.add(Menu.NONE, this.getId(), order, title);
    }
    
    public boolean execute(UserListContextMenu menu, MyAccount ma) {
        MyLog.v(this, "execute started");
        if (mIsAsync) {
            executeAsync1(menu, ma);
        } else {
            executeOnUiThread(menu, ma);
        }
        return false;
    }
    
    private void executeAsync1(final UserListContextMenu menu, final MyAccount ma) {
        AsyncTaskLauncher.execute(TAG,
                new MyAsyncTask<Void, Void, Void>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected Void doInBackground2(Void... params) {
                        MyLog.v(this, "execute async started. "
                                + menu.getViewItem().mbUser.getNamePreferablyWebFingerId());
                        executeAsync(menu, ma);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void v) {
                        MyLog.v(this, "execute async ended");
                        executeOnUiThread(menu, ma);
                    }

                    @Override
                    public String toString() {
                        return TAG + "; " + super.toString();
                    }
                }
        );
    }

    void executeAsync(UserListContextMenu menu, MyAccount ma) {
        // Empty
    }

    void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
        // Empty
    }
    
    void sendCommandUser(CommandEnum command, MyAccount ma, long userID) {
        MyServiceManager.sendManualForegroundCommand(
                new CommandData(command, ma.getAccountName(), userID));
    }
}
