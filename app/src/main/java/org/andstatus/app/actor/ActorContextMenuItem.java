/*
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

package org.andstatus.app.actor;

import android.net.Uri;
import android.view.Menu;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.list.ContextMenuItem;
import org.andstatus.app.note.NoteEditorData;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;

public enum ActorContextMenuItem implements ContextMenuItem {
    GET_ACTOR(true) {
        @Override
        void executeAsync(Params params) {
            CommandData commandData = CommandData.newActorCommand(
                    CommandEnum.GET_ACTOR,
                    params.ma,
                    params.menu.getOrigin(),
                    params.menu.getViewItem().getActorId(),
                    params.menu.getViewItem().actor.getUsername());
            MyServiceManager.sendManualForegroundCommand(commandData);
        }
    },
    PRIVATE_NOTE() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
            NoteEditorData editorData = NoteEditorData.newEmpty(menu.getMyActor())
                    .addRecipientId(menu.getViewItem().getActorId());
            if (editorData.activity.getNote().audience().hasNonPublic()) {
                menu.menuContainer.getNoteEditor().startEditingNote(editorData);
            }
        }
    },
    SHARE() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
            // TODO
        }
    },
    ACTOR_NOTES(true) {
        @Override
        void executeAsync(Params params) {
            TimelineActivity.startForTimeline(params.menu.getActivity().getMyContext(),
                    params.menu.getActivity(),
                    params.menu.getActivity().getMyContext().timelines().get( TimelineType.SENT,
                            params.menu.getViewItem().getActorId(), params.menu.getOrigin(), ""),
                    params.ma, false);
        }
    },
    FOLLOW() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
            sendActorCommand(CommandEnum.FOLLOW, ma, menu);
        }
    },
    STOP_FOLLOWING() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
            sendActorCommand(CommandEnum.UNDO_FOLLOW, ma, menu);
        }
    },
    ACT_AS_FIRST_OTHER_ACCOUNT() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
            menu.setMyActor(ma.firstOtherAccountOfThisOrigin());
            menu.showContextMenu();
        }
    },
    ACT_AS() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
            AccountSelector.selectAccount(menu.getActivity(),
                    ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, ma.getOriginId());
        }
    },
    FOLLOWERS(true) {
        @Override
        void executeAsync(Params params) {
            setMaForActorId(params);
        }

        @Override
        void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
            startActorListActivity(menu, ma, ActorListType.FOLLOWERS);
        }
    },
    FRIENDS(true) {
        @Override
        void executeAsync(Params params) {
            setMaForActorId(params);
        }

        @Override
        void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
            startActorListActivity(menu, ma, ActorListType.FRIENDS);
        }
    },
    NONEXISTENT(),
    UNKNOWN();

    private static class Params {
        ActorContextMenu menu;
        volatile MyAccount ma;

        Params(ActorContextMenu menu, MyAccount ma) {
            this.menu = menu;
            this.ma = ma;
        }
    }

    private final boolean mIsAsync;
    private static final String TAG = ActorContextMenuItem.class.getSimpleName();

    ActorContextMenuItem() {
        this(false);
    }

    ActorContextMenuItem(boolean isAsync) {
        this.mIsAsync = isAsync;
    }

    @Override
    public int getId() {
        return Menu.FIRST + ordinal() + 1;
    }
    
    public static ActorContextMenuItem fromId(int id) {
        for (ActorContextMenuItem item : ActorContextMenuItem.values()) {
            if (item.getId() == id) {
                return item;
            }
        }
        return UNKNOWN;
    }

    public void addTo(Menu menu, int menuGroup, int order, int titleRes) {
        menu.add(menuGroup, this.getId(), order, titleRes);
    }

    public void addTo(Menu menu, int menuGroup, int order, CharSequence title) {
        menu.add(menuGroup, this.getId(), order, title);
    }
    
    public boolean execute(ActorContextMenu menu, MyAccount ma) {
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
        AsyncTaskLauncher.execute(TAG, true,
                new MyAsyncTask<Void, Void, Void>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected Void doInBackground2(Void... params2) {
                        MyLog.v(this, "execute async started. "
                                + params.menu.getViewItem().actor.getNamePreferablyWebFingerId());
                        executeAsync(params);
                        return null;
                    }

                    @Override
                    protected void onPostExecute2(Void v) {
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

    void executeOnUiThread(ActorContextMenu menu, MyAccount ma) {
        // Empty
    }

    void setMaForActorId(Params params) {
        long actorId = params.menu.getViewItem().getActorId();
        Origin origin = params.menu.getOrigin();
        if (!origin.isValid()) {
            MyLog.e(this, "Unknown origin for " + params.menu.getViewItem().actor);
            return;
        }
        if (!params.ma.isValid() || !params.ma.getOrigin().equals(origin)) {
            params.ma = params.menu.getActivity().getMyContext().accounts().fromActorId(actorId);
            if (!params.ma.isValid()) {
                params.ma = params.menu.getActivity().getMyContext().accounts().
                        getFirstSucceededForOrigin(origin);
            }
        }
    }

    void startActorListActivity(ActorContextMenu menu, MyAccount ma, ActorListType actorListType) {
        Uri uri = MatchedUri.getActorListUri(ma.getActorId(),
                actorListType,
                menu.getOrigin().getId(),
                menu.getViewItem().getActorId(), "");
        if (MyLog.isVerboseEnabled()) {
            MyLog.d(this, "startFollowersList, uri:" + uri);
        }
        menu.getActivity().startActivity(MyAction.VIEW_FOLLOWERS.getIntent(uri));
    }

    void sendActorCommand(CommandEnum command, MyAccount myActor, ActorContextMenu menu) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newActorCommand(command, myActor, menu.getOrigin(), menu.getViewItem().getActorId(), ""));
    }
}
