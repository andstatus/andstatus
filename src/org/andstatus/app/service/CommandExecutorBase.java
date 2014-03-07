/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.MyLog;

abstract class CommandExecutorBase implements CommandExecutorStrategy, CommandExecutorParent {
    protected static CommandExecutorStrategy getStrategy(CommandData commandData, MyAccount acc) {
        CommandExecutorStrategy strategy;
        if (acc == null) {
            if (commandData.getTimelineType() == TimelineTypeEnum.PUBLIC) {
                strategy = new CommandExecutorAllOrigins();
            } else {
                strategy = new CommandExecutorAllAccounts();
            }
        } else {
            switch (commandData.getCommand()) {
                case AUTOMATIC_UPDATE:
                case FETCH_TIMELINE:
                case SEARCH_MESSAGE:
                    strategy = new CommandExecutorLoadTimeline();
                    break;
                default:
                    strategy = new CommandExecutorOther();
                    break;
            }
            strategy.setMyAccount(acc);
        }
        strategy.setCommandData(commandData);
        MyLog.d("CommandExecutorStrategy", strategy.getClass().getSimpleName() + " executing " + commandData);
        return strategy;
    }

    protected Context context;
    protected CommandData commandData = null;
    protected MyAccount ma = null;
    private CommandExecutorParent parent = null;

    public CommandExecutorBase() {
        context = MyContextHolder.get().context();
    }
    
    @Override
    public CommandExecutorStrategy setCommandData(CommandData commandData) {
        this.commandData = commandData;
        return this;
    }

    @Override
    public CommandExecutorStrategy setMyAccount(MyAccount ma) {
        this.ma = ma;
        return this;
    }
    
    @Override
    public CommandExecutorStrategy setParent(CommandExecutorParent parent) {
        this.parent = parent;
        return this;
    }
    
    protected void setSoftErrorIfNotOk(CommandData commandData, boolean ok) {
        if (!ok) {
            commandData.getResult().incrementNumIoExceptions();
        }
    }

    @Override
    public boolean isStopping() {
        if (parent != null) {
            return parent.isStopping();
        } else {
            return false;
        }
    }

    protected void logConnectionException(ConnectionException e, CommandData commandData, String detailedMessage) {
        if (e.isHardError()) {
            commandData.getResult().incrementParseExceptions();
        } else {
            commandData.getResult().incrementNumIoExceptions();
        }
        MyLog.e(this, detailedMessage + ": " + e.toString());
    }
}
