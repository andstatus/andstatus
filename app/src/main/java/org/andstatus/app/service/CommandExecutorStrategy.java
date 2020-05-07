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

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TryUtils;

import io.vavr.control.Try;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

class CommandExecutorStrategy implements CommandExecutorParent {
    final protected CommandExecutionContext execContext;
    private CommandExecutorParent parent = null;
    protected static final long MIN_PROGRESS_BROADCAST_PERIOD_SECONDS = 1;
    protected long lastProgressBroadcastAt  = 0;

    static void executeCommand(CommandData commandData, CommandExecutorParent parent) {
        CommandExecutorStrategy strategy = getStrategy(
            new CommandExecutionContext(commandData.myAccount.getOrigin().myContext, commandData)).setParent(parent);
        commandData.getResult().prepareForLaunch();
        logLaunch(strategy);
        // This may cause recursive calls to executors...
        strategy.execute()
        .onSuccess(ok -> {
            strategy.execContext.getResult().setSoftErrorIfNotOk(ok);
            MyLog.d(strategy, strategy.execContext.getCommandSummary() + (ok ? " succeeded" : " soft errors"));
        })
        .onFailure(t -> strategy.logException(t, strategy.execContext.getCommandSummary()));
        commandData.getResult().afterExecutionEnded();
        logEnd(strategy);
    }

    private static void logLaunch(CommandExecutorStrategy strategy) {
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

    protected <T> Try<T> onParseException(String message) {
        execContext.getResult().incrementParseExceptions();
        MyLog.e(this, message);
        return TryUtils.failure(message);
    }

    private static void logEnd(CommandExecutorStrategy strategy) {
        MyLog.d(strategy, "Executed " + strategy.execContext);
    }

    void broadcastProgress(String progress, boolean notTooOften) {
        if (notTooOften){
            if (!RelativeTime.moreSecondsAgoThan(lastProgressBroadcastAt, MIN_PROGRESS_BROADCAST_PERIOD_SECONDS)) {
                return;
            }
        }
        MyLog.v(this, () -> "Progress: " + progress);
        lastProgressBroadcastAt = System.currentTimeMillis();
        MyServiceEventsBroadcaster.newInstance(myContextHolder.getNow(), MyServiceState.RUNNING)
                .setCommandData(execContext.getCommandData())
                .setProgress(progress)
                .setEvent(MyServiceEvent.PROGRESS_EXECUTING_COMMAND).broadcast();
    }

    static CommandExecutorStrategy getStrategy(CommandData commandData, CommandExecutorParent parent) {
        return getStrategy(
                new CommandExecutionContext(commandData.myAccount.getOrigin().myContext, commandData)).setParent(parent);
    }

    private static CommandExecutorStrategy getStrategy(CommandExecutionContext execContext) {
        CommandExecutorStrategy strategy;
        switch (execContext.getCommandData().getCommand()) {
            case GET_ATTACHMENT:
            case GET_AVATAR:
                strategy = new CommandExecutorOther(execContext);
                break;
            case GET_OPEN_INSTANCES:
                strategy = new CommandExecutorGetOpenInstances(execContext);
                break;
            default:
                if (execContext.getMyAccount().isValidAndSucceeded()) {
                    switch (execContext.getCommandData().getCommand()) {
                        case GET_TIMELINE:
                        case GET_OLDER_TIMELINE:
                            if (execContext.getCommandData().getTimeline().isSyncable()) {
                                switch (execContext.getCommandData().getTimelineType()) {
                                    case FOLLOWERS:
                                    case FRIENDS:
                                        strategy = new TimelineDownloaderFollowers(execContext);
                                        break;
                                    default:
                                        strategy = new TimelineDownloaderOther(execContext);
                                        break;
                                }
                            } else {
                                MyLog.v(CommandExecutorStrategy.class, () -> "Dummy commandExecutor for " +
                                        execContext.getCommandData().getTimeline());
                                strategy = new CommandExecutorStrategy(execContext);
                            }
                            break;
                        case GET_FOLLOWERS:
                        case GET_FRIENDS:
                            strategy = new CommandExecutorFollowers(execContext);
                            break;
                        default:
                            strategy = new CommandExecutorOther(execContext);
                            break;
                    }
                } else {
                    MyLog.v(CommandExecutorStrategy.class, () -> "Dummy commandExecutor for "
                            + execContext.getMyAccount());
                    strategy = new CommandExecutorStrategy(execContext);
                }
                break;
        }
        return strategy;
    }

    CommandExecutorStrategy(CommandExecutionContext execContext) {
        this.execContext = execContext;
    }

    private CommandExecutorStrategy setParent(CommandExecutorParent parent) {
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

    <T> Try<T> logException(Throwable t, String detailedMessage) {
        ConnectionException e = ConnectionException.of(t);
        boolean isHard = t != null && e.isHardError();
        MyStringBuilder builder = MyStringBuilder.of(detailedMessage);
        if (t != null) {
            builder.atNewLine(e.toString());
        }
        return logExecutionError(isHard, builder.toString());
    }

    <T> Try<T> logExecutionError(boolean isHard, String detailedMessage) {
        if (isHard) {
            execContext.getResult().incrementParseExceptions();
        } else {
            execContext.getResult().incrementNumIoExceptions();
        }
        MyStringBuilder builder = MyStringBuilder.of(detailedMessage).atNewLine(execContext.toExceptionContext());
        execContext.getResult().setMessage(builder.toString());
        MyLog.e(this, builder.toString());
        return TryUtils.failure(detailedMessage);
    }

    /** @return Success and false means soft error occurred */
    Try<Boolean> execute() {
        MyLog.d(this, "Doing nothing");
        return Try.success(true);
    }

    boolean noErrors() {
        return !execContext.getResult().hasError();
    }

    Actor getActor() {
        return execContext.getCommandData().getTimeline().actor;
    }

    boolean isApiSupported(ApiRoutineEnum routine) {
        return getConnection().hasApiEndpoint(routine);
    }

    public Connection getConnection() {
        return execContext.getConnection();
    }
}
