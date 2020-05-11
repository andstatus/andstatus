package org.andstatus.app.service;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

class QueueExecutor extends MyAsyncTask<Void, Void, Boolean> implements CommandExecutorParent {
    static final long MAX_EXECUTION_TIME_SECONDS = 60;
    private final static String TAG = "QueueExecutor";
    private final WeakReference<MyService> myServiceRef;
    private final CommandQueue.AccessorType accessorType;
    private final AtomicLong executedCounter = new AtomicLong();

    QueueExecutor(MyService myService, CommandQueue.AccessorType accessorType) {
        super(TAG + "-" + accessorType, PoolEnum.SYNC);
        this.myServiceRef = new WeakReference<>(myService);
        this.accessorType = accessorType;
    }

    @Override
    public String instanceTag() {
        return super.instanceTag() + "-" + accessorType;
    }

    @Override
    protected Boolean doInBackground2(Void aVoid) {
        MyService myService = myServiceRef.get();
        if (myService == null) {
            MyLog.v(this, () -> "Didn't start, no reference to MyService");
            return true;
        }

        CommandQueue.Accessor accessor = myService.myContext.queues().getAccessor(accessorType);
        accessor.moveCommandsFromSkippedToMainQueue();
        MyLog.v(this, () -> "Started, " + (accessor.isAnythingToExecuteNow() ? "some" : "no") + " commands to process");
        final String breakReason;
        do {
            if (isStopping()) {
                breakReason = "isStopping";
                break;
            }
            if (isCancelled()) {
                breakReason = "Cancelled";
                break;
            }
            if (RelativeTime.secondsAgo(backgroundStartedAt) > MAX_EXECUTION_TIME_SECONDS) {
                breakReason = "Executed too long";
                break;
            }
            if (myService.executors.getRef(accessorType).get() != this) {
                breakReason = "Other executor";
                break;
            }
            CommandData commandData = accessor.pollQueue();
            if (commandData == null) {
                breakReason = "No more commands";
                break;
            }

            executedCounter.incrementAndGet();
            currentlyExecutingSince = System.currentTimeMillis();
            currentlyExecutingDescription = commandData.toString();
            myService.broadcastBeforeExecutingCommand(commandData);

            CommandExecutorStrategy.executeCommand(commandData, this);

            if (commandData.getResult().shouldWeRetry()) {
                myService.myContext.queues().addToQueue(QueueType.RETRY, commandData);
            } else if (commandData.getResult().hasError()) {
                myService.myContext.queues().addToQueue(QueueType.ERROR, commandData);
            } else {
                addSyncAfterNoteWasSent(myService, commandData);
            }
            myService.broadcastAfterExecutingCommand(commandData);
        } while (true);
        MyLog.v(this, () -> "Ended, " + myService.myContext.queues().totalSizeToExecute() + " commands left");
        myService.myContext.queues().save();

        currentlyExecutingSince = 0;
        currentlyExecutingDescription = breakReason;
        return true;
    }

    private void addSyncAfterNoteWasSent(MyService myService, CommandData commandDataExecuted) {
        if (commandDataExecuted.getResult().hasError()
                || commandDataExecuted.getCommand() != CommandEnum.UPDATE_NOTE
                || !SharedPreferencesUtil.getBoolean(
                        MyPreferences.KEY_SYNC_AFTER_NOTE_WAS_SENT, false)) {
            return;
        }
        myService.myContext.queues().addToQueue(QueueType.CURRENT, CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                commandDataExecuted.getTimeline().myAccountToSync, TimelineType.SENT)
                .setInForeground(commandDataExecuted.isInForeground()));
    }

    @Override
    protected void onFinish(Boolean aBoolean, boolean success) {
        MyService myService = myServiceRef.get();
        if (myService != null) {
            myService.latestActivityTime = System.currentTimeMillis();
        }

        MyLog.v(this, (success ? "onExecutorSuccess" : "onExecutorFailure")
            + (executedCounter.get() == 0 ? " no commands executed" : executedCounter.get() + " commands executed")
        );
        currentlyExecutingSince = 0;
        if (myService != null) {
            myService.reviveHeartBeat();
            myService.startStopExecution();
        }
    }

    @Override
    public boolean isStopping() {
        MyService myService = myServiceRef.get();
        return myService == null || myService.isStopping();
    }

    @Override
    public String classTag() {
        return TAG;
    }

    @Override
    public String toString() {
        MyStringBuilder sb = new MyStringBuilder();
        if (currentlyExecutingSince == 0) {
            sb.withComma("notExecuting", currentlyExecutingDescription);
        } else {
            sb.withComma("executing", currentlyExecutingDescription);
            sb.withComma("since", RelativeTime.getDifference(myContextHolder.getNow().context(), currentlyExecutingSince));
        }
        if (isStopping()) {
            sb.withComma("stopping");
        }
        sb.withComma(super.toString());
        return sb.toKeyValue(this);
    }

}
