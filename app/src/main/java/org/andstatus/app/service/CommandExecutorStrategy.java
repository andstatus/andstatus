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

import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.util.MyLog;

class CommandExecutorStrategy implements CommandExecutorParent {
    protected CommandExecutionContext execContext = null;
    private CommandExecutorParent parent = null;

    static void executeCommand(CommandData commandData, CommandExecutorParent parent) {
        CommandExecutorStrategy strategy = getStrategy(new CommandExecutionContext(commandData, commandData.getAccount()))
                .setParent(parent);
        commandData.getResult().prepareForLaunch();
        logLaunch(strategy);
        // This may cause recursive calls to executors...
        strategy.execute();
        commandData.getResult().afterExecutionEnded();
        logEnd(strategy);
    }

    private static void logLaunch(CommandExecutorStrategy strategy) {
        if (strategy.execContext.getCommandData().getCommand() == CommandEnum.UPDATE_STATUS) {
            MyLog.onSendingMessageStart();
        }
        MyLog.d(strategy, "Launching " + strategy.execContext);
    }

    public boolean logSoftErrorIfStopping() {
        if (isStopping()) {
            if ( !execContext.getResult().hasError()) {
                execContext.getResult().incrementNumIoExceptions();
                execContext.getResult().setMessage("Service is stopping");
            }
            return true;
        }
        return false;
    }

    private static void logEnd(CommandExecutorStrategy strategy) {
        MyLog.d(strategy, "Executed " + strategy.execContext);
        if (strategy.execContext.getCommandData().getCommand() == CommandEnum.UPDATE_STATUS) {
            MyLog.onSendingMessageEnd();
        }
    }

    static void executeStep(CommandExecutionContext execContext, CommandExecutorParent parent) {
        execContext.onOneExecStepLaunch();
        CommandExecutorStrategy strategy = getStrategy(execContext).setParent(parent);
        MyLog.v(strategy, "LaunchingStep " + strategy.execContext);
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.RUNNING)
                .setCommandData(execContext.getCommandData())
                .setEvent(MyServiceEvent.BEFORE_EXECUTING_COMMAND).broadcast();
        strategy.execute();
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.RUNNING)
                .setCommandData(execContext.getCommandData())
                .setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
        MyLog.v(strategy, "ExecutedStep " + strategy.execContext);
        execContext.onOneExecStepEnd();
    }
    
    static CommandExecutorStrategy getStrategy(CommandData commandData, CommandExecutorParent parent) {
        return getStrategy(new CommandExecutionContext(commandData, commandData.getAccount()))
                .setParent(parent);
    }

    static CommandExecutorStrategy getStrategy(CommandExecutionContext execContext) {
        CommandExecutorStrategy strategy;
        switch (execContext.getCommandData().getCommand()) {
            case FETCH_ATTACHMENT:
            case FETCH_AVATAR:
                strategy = new CommandExecutorOther();
                break;
            case GET_OPEN_INSTANCES:
                strategy = new CommandExecutorGetOpenInstances();
                break;
            default:
                if (!execContext.getMyAccount().isValid()) {
                    if (execContext.getTimelineType() == TimelineType.PUBLIC) {
                        strategy = new CommandExecutorAllOrigins();
                    } else {
                        strategy = new CommandExecutorAllAccounts();
                    }
                } else if (execContext.getMyAccount().isValidAndSucceeded()) {
                    switch (execContext.getCommandData().getCommand()) {
                        case AUTOMATIC_UPDATE:
                        case FETCH_TIMELINE:
                            switch (execContext.getTimelineType()) {
                                case ALL:
                                case EVERYTHING:
                                    strategy = new CommandExecutorLoadAllTimelines();
                                    break;
                                default:
                                    strategy = new CommandExecutorLoadTimeline();
                                    break;
                            }
                            break;
                        case SEARCH_MESSAGE:
                            strategy = new CommandExecutorSearch();
                            break;
                        case GET_FOLLOWERS:
                            strategy = new CommandExecutorFollowers();
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
        if (e != null && e.isHardError()) {
            execContext.getResult().incrementParseExceptions();
        } else {
            execContext.getResult().incrementNumIoExceptions();
        }
        StringBuilder builder = new StringBuilder(100);
        appendAtNewLine(builder, detailedMessage);
        if (e != null) {
            appendAtNewLine(builder, e.toString());
        }
        appendAtNewLine(builder, execContext.toExceptionContext());
        execContext.getResult().setMessage(builder.toString());
        MyLog.e(this, builder.toString());
    }

    public static void appendAtNewLine(StringBuilder builder, String string) {
        if (!TextUtils.isEmpty(string)) {
            if (builder.length() > 0) {
                builder.append(", \n");
            }
            builder.append(string);
        }
    }

    void execute() {
        MyLog.d(this, "Doing nothing");
    }

    protected void logOk(boolean ok) {
        execContext.getResult().setSoftErrorIfNotOk(ok);
    }
}
