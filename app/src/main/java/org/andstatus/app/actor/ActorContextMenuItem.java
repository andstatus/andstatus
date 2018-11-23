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
import org.andstatus.app.net.social.Actor;
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
        NoteEditorData executeAsync(Params params) {
            CommandData commandData = CommandData.newActorCommand(CommandEnum.GET_ACTOR,
                    params.menu.getViewItem().getActorId(),
                    params.menu.getViewItem().actor.getUsername());
            MyServiceManager.sendManualForegroundCommand(commandData);
            return super.executeAsync(params);
        }
    },
    PRIVATE_NOTE(true) {
        @Override
        NoteEditorData executeAsync(Params params) {
            return NoteEditorData.newEmpty(params.menu.getActingAccount())
                    .addToAudience(params.menu.getViewItem().getActorId());
        }

        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            if (editorData.activity.getNote().audience().hasNonPublic()) {
                menu.menuContainer.getNoteEditor().startEditingNote(editorData);
            }
        }
    },
    SHARE() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            // TODO
        }
    },
    NOTES_BY_ACTOR(true) {
        @Override
        NoteEditorData executeAsync(Params params) {
            TimelineActivity.startForTimeline(
                    params.menu.getActivity().getMyContext(),
                    params.menu.getActivity(),
                    params.menu.getActivity().getMyContext().timelines()
                            .forUserAtHomeOrigin(TimelineType.SENT, params.menu.getViewItem().getActor()),
                    false);
            return super.executeAsync(params);
        }
    },
    FOLLOW() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            sendActOnActorCommand(CommandEnum.FOLLOW, menu);
        }
    },
    STOP_FOLLOWING() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            sendActOnActorCommand(CommandEnum.UNDO_FOLLOW, menu);
        }
    },
    ACT_AS_FIRST_OTHER_ACCOUNT() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            menu.setSelectedActingAccount(menu.getMyContext().accounts()
                    .firstOtherSucceededForSameUser(menu.getViewItem().actor, menu.getActingAccount()));
            menu.showContextMenu();
        }
    },
    ACT_AS() {
        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            AccountSelector.selectAccountForActor(menu.getActivity(), menu.menuGroup,
                    ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, menu.getViewItem().getActor());
        }
    },
    FOLLOWERS(true) {
        @Override
        NoteEditorData executeAsync(Params params) {
            setActingAccountForActor(params);
            return super.executeAsync(params);
        }

        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            startActorListActivity(menu, ActorListType.FOLLOWERS);
        }
    },
    FRIENDS(true) {
        @Override
        NoteEditorData executeAsync(Params params) {
            setActingAccountForActor(params);
            return super.executeAsync(params);
        }

        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            startActorListActivity(menu, ActorListType.FRIENDS);
        }
    },
    NONEXISTENT(),
    UNKNOWN();

    private static class Params {
        ActorContextMenu menu;

        Params(ActorContextMenu menu) {
            this.menu = menu;
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
        Params params = new Params(menu);
        MyLog.v(this, "execute started");
        if (mIsAsync) {
            executeAsync1(params);
        } else {
            executeOnUiThread(params.menu,
                    new NoteEditorData(menu.getMyContext(),
                            menu.getActingAccount(), 0, 0, false));
        }
        return false;
    }
    
    private void executeAsync1(final Params params) {
        AsyncTaskLauncher.execute(TAG, true,
                new MyAsyncTask<Void, Void, NoteEditorData>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected NoteEditorData doInBackground2(Void... params2) {
                        MyLog.v(this, "execute async started. "
                                + params.menu.getViewItem().actor.getNamePreferablyWebFingerId());
                        return executeAsync(params);
                    }

                    @Override
                    protected void onPostExecute2(NoteEditorData editorData) {
                        MyLog.v(this, "execute async ended");
                        executeOnUiThread(params.menu, editorData);
                    }

                    @Override
                    public String toString() {
                        return TAG + "; " + super.toString();
                    }
                }
        );
    }

    NoteEditorData executeAsync(Params params) {
        return NoteEditorData.newEmpty(params.menu.getActingAccount());
    }

    void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
        // Empty
    }

    void setActingAccountForActor(Params params) {
        Actor actor = params.menu.getViewItem().getActor();
        Origin origin = params.menu.getOrigin();
        if (!origin.isValid()) {
            MyLog.e(this, "Unknown origin for " + params.menu.getViewItem().actor);
            return;
        }
        if (params.menu.getActingAccount().nonValid() || !params.menu.getActingAccount().getOrigin().equals(origin)) {
            params.menu.setSelectedActingAccount(params.menu.getMyContext().accounts()
                    .fromActorOfSameOrigin(actor));
            if (params.menu.getActingAccount().nonValid()) {
                params.menu.setSelectedActingAccount(params.menu.getMyContext().accounts().
                        getFirstSucceededForOrigin(origin));
            }
        }
    }

    void startActorListActivity(ActorContextMenu menu, ActorListType actorListType) {
        Uri uri = MatchedUri.getActorListUri(
                actorListType,
                menu.getOrigin().getId(),
                menu.getViewItem().getActorId(), "");
        if (MyLog.isVerboseEnabled()) {
            MyLog.d(this, "startFollowersList, uri:" + uri);
        }
        menu.getActivity().startActivity(MyAction.VIEW_FOLLOWERS.getIntent(uri));
    }

    void sendActOnActorCommand(CommandEnum command, ActorContextMenu menu) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.actOnActorCommand(command, menu.getActingAccount(),
                        menu.getViewItem().getActorId(), menu.getViewItem().actor.getUsername()));
    }
}
