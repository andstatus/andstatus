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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.MyLog;

class CommandExecutorStrategy implements CommandExecutorParent {
    protected CommandExecutionContext execContext = null;
    private CommandExecutorParent parent = null;

    static CommandExecutorStrategy getStrategy(CommandData commandData, CommandExecutorParent parent) {
        return getStrategy(new CommandExecutionContext(commandData, commandData.getAccount()))
                .setParent(parent);
    }

    static CommandExecutorStrategy getStrategy(CommandExecutionContext execContext) {
        CommandExecutorStrategy strategy;
        switch (execContext.getCommandData().getCommand()) {
            case FETCH_AVATAR:
                strategy = new CommandExecutorOther();
                break;
            default:
                if (execContext.getMyAccount() == null) {
                    if (execContext.getTimelineType() == TimelineTypeEnum.PUBLIC) {
                        strategy = new CommandExecutorAllOrigins();
                    } else {
                        strategy = new CommandExecutorAllAccounts();
                    }
                } else if (execContext.getMyAccount().getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED
                        && execContext.getMyAccount().isValid()) {
                    switch (execContext.getCommandData().getCommand()) {
                        case AUTOMATIC_UPDATE:
                        case FETCH_TIMELINE:
                            strategy = new CommandExecutorLoadTimeline();
                            break;
                        case SEARCH_MESSAGE:
                            strategy = new CommandExecutorSearch();
                            break;
                        default:
                            strategy = new CommandExecutorOther();
                            break;
                    }
                } else {
                    strategy = new CommandExecutorStrategy();
                }
                break;
        }
        strategy.setContext(execContext);
        MyLog.d("CommandExecutorStrategy", strategy.getClass().getSimpleName() + " executing "
                + execContext);
        return strategy;
    }
    
    static CommandExecutorStrategy newInstance(Class<? extends CommandExecutorStrategy> clazz, CommandExecutionContext execContextIn) {
        CommandExecutorStrategy exec = null;
        try {
            exec = clazz.newInstance();
            if (execContextIn == null) {
                exec.execContext = new CommandExecutionContext(CommandData.getEmpty(), null);
            } else {
                exec.execContext = execContextIn;
            }
        } catch (InstantiationException e) {
            MyLog.e(CommandExecutorStrategy.class, "class=" + clazz, e);
        } catch (IllegalAccessException e) {
            MyLog.e(CommandExecutorStrategy.class, "class=" + clazz, e);
        }
        return exec;
    }

    CommandExecutorStrategy() {
    }
    
    CommandExecutorStrategy setContext(CommandExecutionContext execContext) {
        this.execContext = execContext;
        return this;
    }
    
    CommandExecutorStrategy setMyAccount(MyAccount ma) {
        execContext.setMyAccount(ma);
        return this;
    }
    
    CommandExecutorStrategy setParent(CommandExecutorParent parent) {
        this.parent = parent;
        return this;
    }
    
    @Override
    public boolean isStopping() {
        if (parent != null) {
            return parent.isStopping();
        } else {
            return false;
        }
    }

    void logConnectionException(ConnectionException e, String detailedMessage) {
        if (e.isHardError()) {
            execContext.getResult().incrementParseExceptions();
        } else {
            execContext.getResult().incrementNumIoExceptions();
        }
        MyLog.e(this, detailedMessage + ": " + e.toString());
    }

    void execute() {
        MyLog.d(this, "Doing nothing");
    }
}
