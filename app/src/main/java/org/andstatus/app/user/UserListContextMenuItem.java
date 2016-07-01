/**
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.msg.TimelineActivity;
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
            CommandData commandData = CommandData.newUserCommand(
                    CommandEnum.GET_USER,
                    MyContextHolder.get().persistentOrigins().fromId(menu.getViewItem().mbUser.originId),
                    menu.getViewItem().getUserId(),
                    menu.getViewItem().mbUser.getUserName());
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
            intent.setData(MatchedUri.getTimelineUri(
                    Timeline.getTimeline(menu.getActivity().getMyContext(), TimelineType.USER, ma,
                            menu.getViewItem().getUserId(), null, "")));
            menu.getActivity().startActivity(intent);
        }
    },
    FOLLOW() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            sendUserCommand(CommandEnum.FOLLOW_USER, menu.getViewItem());
        }
    },
    STOP_FOLLOWING() {
        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            sendUserCommand(CommandEnum.STOP_FOLLOWING_USER, menu.getViewItem());
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
            AccountSelector.selectAccount(menu.getActivity(), ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, ma.getOriginId());
        }
    },
    FOLLOWERS(true) {
        @Override
        void executeAsync(Params params) {
            setMaForUserId(params);
        }

        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            startFollowersList(menu, ma, UserListType.FOLLOWERS);
        }
    },
    FRIENDS(true) {
        @Override
        void executeAsync(Params params) {
            setMaForUserId(params);
        }

        @Override
        void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
            startFollowersList(menu, ma, UserListType.FRIENDS);
        }
    },
    NONEXISTENT(),
    UNKNOWN();

    private static class Params {
        UserListContextMenu menu;
        volatile MyAccount ma;

        Params(UserListContextMenu menu, MyAccount ma) {
            this.menu = menu;
            this.ma = ma;
        }
    }

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
        Params params = new Params(menu, ma);
        MyLog.v(this, "execute started");
        if (mIsAsync) {
            executeAsync1(params);
        } else {
            executeOnUiThread(params.menu, params.ma);
        }
        return false;
    }
    
    private void executeAsync1(final Params params) {
        AsyncTaskLauncher.execute(TAG,
                new MyAsyncTask<Void, Void, Void>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected Void doInBackground2(Void... params2) {
                        MyLog.v(this, "execute async started. "
                                + params.menu.getViewItem().mbUser.getNamePreferablyWebFingerId());
                        executeAsync(params);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void v) {
                        MyLog.v(this, "execute async ended");
                        executeOnUiThread(params.menu, params.ma);
                    }

                    @Override
                    public String toString() {
                        return TAG + "; " + super.toString();
                    }
                }
        );
    }

    void executeAsync(Params params) {
        // Empty
    }

    void executeOnUiThread(UserListContextMenu menu, MyAccount ma) {
        // Empty
    }

    void setMaForUserId(Params params) {
        long userId = params.menu.getViewItem().getUserId();
        long originId = MyQuery.userIdToLongColumnValue(UserTable.ORIGIN_ID, userId);
        if (originId == 0) {
            MyLog.e(this, "Unknown origin for userId=" + userId);
            return;
        }
        if (!params.ma.isValid() || params.ma.getOriginId() != originId) {
            params.ma = MyContextHolder.get().persistentAccounts().fromUserId(userId);
            if (!params.ma.isValid()) {
                params.ma = MyContextHolder.get().persistentAccounts().getFirstSucceededForOriginId(originId);
            }
        }
    }

    void startFollowersList(UserListContextMenu menu, MyAccount ma, UserListType userListType) {
        Uri uri = MatchedUri.getUserListUri(ma.getUserId(),
                userListType,
                ma.getOriginId(),
                menu.getViewItem().getUserId());
        if (MyLog.isVerboseEnabled()) {
            MyLog.d(this, "startFollowersList, uri:" + uri);
        }
        menu.getActivity().startActivity(MyAction.VIEW_FOLLOWERS.getIntent(uri));
    }

    void sendUserCommand(CommandEnum command, UserListViewItem viewItem) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newUserCommand(
                        command,
                        MyContextHolder.get().persistentOrigins().fromId(viewItem.mbUser.originId),
                        viewItem.getUserId(),
                        ""
                )
        );
    }
}
