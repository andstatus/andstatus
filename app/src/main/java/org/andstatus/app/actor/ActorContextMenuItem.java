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

import java.util.Collections;

public enum ActorContextMenuItem implements ContextMenuItem {
    GET_ACTOR(true) {
        @Override
        NoteEditorData executeAsync(Params params) {
            params.menu.getViewItem().actor.requestDownload(true);
            return super.executeAsync(params);
        }
    },
    POST_TO(true) {
        @Override
        NoteEditorData executeAsync(Params params) {
            return NoteEditorData.newEmpty(params.menu.getActingAccount())
                    .setPublicAndFollowers(false, false)
                    .addActorsBeforeText(Collections.singletonList(params.menu.getViewItem().actor));
        }

        @Override
        void executeOnUiThread(ActorContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
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
                            .forUserAtHomeOrigin(TimelineType.SENT, params.menu.getViewItem().getActor())
            );
            return super.executeAsync(params);
        }
    },
    GROUP_NOTES(true) {
        @Override
        NoteEditorData executeAsync(Params params) {
            TimelineActivity.startForTimeline(
                    params.menu.getActivity().getMyContext(),
                    params.menu.getActivity(),
                    params.menu.getActivity().getMyContext().timelines()
                            .forUserAtHomeOrigin(TimelineType.GROUP, params.menu.getViewItem().getActor())
            );
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
            startActorsScreen(menu, ActorsScreenType.FOLLOWERS);
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
            startActorsScreen(menu, ActorsScreenType.FRIENDS);
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
    
    public boolean execute(ActorContextMenu menu) {
        Params params = new Params(menu);
        MyLog.v(this, "execute started");
        if (mIsAsync) {
            executeAsync1(params);
        } else {
            MyAccount myAccount = menu.getActingAccount().getValidOrCurrent(menu.getMyContext());
            NoteEditorData editorData = new NoteEditorData(myAccount, 0, false, 0, false);
            executeOnUiThread(params.menu, editorData);
        }
        return false;
    }
    
    private void executeAsync1(final Params params) {
        AsyncTaskLauncher.execute(TAG,
                new MyAsyncTask<Void, Void, NoteEditorData>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected NoteEditorData doInBackground2(Void aVoid) {
                        MyLog.v(this, "execute async started. "
                                + params.menu.getViewItem().actor.getUniqueNameWithOrigin());
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
            MyLog.w(this, "Unknown origin for " + params.menu.getViewItem().actor);
            return;
        }
        if (params.menu.getActingAccount().nonValid() || !params.menu.getActingAccount().getOrigin().equals(origin)) {
            params.menu.setSelectedActingAccount(params.menu.getMyContext().accounts()
                    .fromActorOfSameOrigin(actor));
            if (params.menu.getActingAccount().nonValid()) {
                params.menu.setSelectedActingAccount(params.menu.getMyContext().accounts().
                        getFirstPreferablySucceededForOrigin(origin));
            }
        }
    }

    void startActorsScreen(ActorContextMenu menu, ActorsScreenType actorsScreenType) {
        Uri uri = MatchedUri.getActorsScreenUri(
                actorsScreenType,
                menu.getOrigin().getId(),
                menu.getViewItem().getActorId(), "");
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "startActorsScreen, " + actorsScreenType + ", uri:" + uri);
        }
        menu.getActivity().startActivity(MyAction.VIEW_FOLLOWERS.getIntent(uri));
    }

    void sendActOnActorCommand(CommandEnum command, ActorContextMenu menu) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.actOnActorCommand(command, menu.getActingAccount(),
                        menu.getViewItem().actor, menu.getViewItem().actor.getUsername()));
    }
}
