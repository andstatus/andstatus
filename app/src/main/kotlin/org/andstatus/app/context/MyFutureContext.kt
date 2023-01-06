/*
 * Copyright (C) 2023 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.context

import android.content.Intent
import android.util.AndroidRuntimeException
import io.vavr.control.Try
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.andstatus.app.FirstActivity
import org.andstatus.app.HelpActivity
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.TlsSniSocketFactory
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.ExceptionsCounter
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.syncadapter.SyncInitiator
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.Identified
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.Taggable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/**
 * @author yvolk@yurivolkov.com
 */
class MyFutureContext private constructor(
    private val previousContext: MyContext,
    val future: AsyncResult<MyContext, MyContext>,
    val queue: BlockingQueue<MyContextAction> = ArrayBlockingQueue(20),
    private val identifiable: Identifiable = Identified(MyFutureContext::class)
) : Identifiable by identifiable {

    val createdAt = MyLog.uniqueCurrentTimeMS
    private val actionTaskRef: AtomicReference<AsyncResult<Unit, Unit>?> = AtomicReference()
    override val instanceId = InstanceId.next()

    fun onActionTaskExecuted(task: AsyncResult<Unit, Unit>, myContextAction: MyContextAction, result: Try<Unit>) {
        if (result.isSuccess) {
            MyLog.v(this, "Executed $myContextAction, result:${result.get()}")
        } else {
            MyLog.w(this, "Failed $myContextAction, result:${result.cause}")
        }
        checkQueueExecutor(task, false)
    }

    fun releaseNow(reason: Supplier<String>): MyFutureContext {
        val previousContext = getNow()
        release(this, previousContext, reason)
        return completed(previousContext)
    }

    val isCompletedExceptionally: Boolean get() = future.isFinished && future.result.isFailure

    val isReady: Boolean get() = getNow().isReady

    fun getNow(): MyContext {
        return future.result.getOrElse(previousContext)
    }

    /** Immediately get current state of MyContext initialization,
     * error if not completed yet or if completed with error */
    val tryCurrent: Try<MyContext> get() = future.result

    fun whenSuccessAsync(mainThread: Boolean, consumer: (MyContext) -> Unit) =
        with("whenSuccessAsync", mainThread) { tryMyContext -> tryMyContext.map { consumer(it) } }

    fun with(
        actionName: String,
        mainThread: Boolean = true,
        action: (Try<MyContext>) -> Try<Unit>
    ) = with(MyContextAction(actionName, action, mainThread))

    fun with(myContextAction: MyContextAction) {
        queue.put(myContextAction)
        MyLog.d(instanceTag, "with: $myContextAction")
        checkQueueExecutor(null, false)
    }

    private fun checkQueueExecutor(taskThatEnded: AsyncResult<Unit, Unit>?, futureFinishing: Boolean) {
        if (!futureFinishing && !future.isFinished) {
            MyLog.v(
                this,
                "CheckQueue; Future is not finished:$future Task ended: $taskThatEnded, actions in queue:${queue.size}"
            )
            return
        }
        synchronized(queue) {
            MyLog.v(
                this, "CheckQueue; " + (if (futureFinishing) "Future finishing, " else "") +
                    (if (taskThatEnded != null) "Task ended: $taskThatEnded, " else "") +
                    "actions in queue:${queue.size}"
            )
            if (taskThatEnded == null) {
                if (actionTaskRef.get()?.isFinished ?: true) {
                    queue.poll()?.let { action ->
                        action.newTask(this).let { task ->
                            actionTaskRef.set(task)
                            task.execute(Unit)
                        }
                    }
                }
            } else if (taskThatEnded === actionTaskRef.get()) {
                queue.poll()?.let { action ->
                    action.newTask(this).let { task ->
                        actionTaskRef.set(task)
                        task.execute(Unit)
                    }
                }
            }
        }
    }

    // TODO: Avoid blocking
    fun tryBlocking(): Try<MyContext>  {
        for (i in 0..9) {
            DbUtils.waitMs(FirstActivity::class, 100)
            if (future.isFinished) break
        }
        return future.result
    }

    companion object {
        private val TAG: String = MyFutureContext::class.simpleName!!

        fun fromPrevious(previousFuture: MyFutureContext, calledByIn: Any?, duringUpgrade: Boolean): MyFutureContext {
            val previousContext = previousFuture.getNow()
            if (!duringUpgrade) {
                if (previousContext.state == MyContextState.UPGRADING) {
                    MyLog.v(previousFuture) { "Won't initialize as is upgrading: $previousContext"}
                    return previousFuture
                }
                if (previousFuture.future.isRunning) {
//                    previousFuture.future.cancel()
//                    MyLog.v(previousFuture) { "Cancelled previous future: ${previousFuture.future}"}
                    MyLog.v(previousFuture) { "Won't initialize as is running: ${previousFuture.future}"}
                    return previousFuture
                }
            }
            val calledBy: Any = calledByIn ?: previousContext
            val reason: String = (
                if (duringUpgrade) {
                    "During upgrade"
                } else if (!previousContext.isReady) {
                    "Context not ready"
                } else if (previousContext.isExpired) {
                    "Context expired"
                } else if (previousContext.isPreferencesChanged) {
                    "Preferences changed"
                } else {
                    MyLog.v(previousFuture) { "Won't initialize as is ready: $previousContext"}
                    return previousFuture
                }) +
                ", previous:" + previousContext +
                ", called by " + Taggable.anyToTag(calledBy)
            MyLog.d(previousFuture) { "Will initialize: $reason \n${AsyncTaskLauncher.threadPoolInfo}"}

            val future = AsyncResult<MyContext, MyContext>("$calledBy${InstanceId.next()}", AsyncEnum.DEFAULT_POOL, false)
                .doInBackground {
                    Try.of {
                        val reasonSupplier = Supplier { "Initialization: $reason" }
                        MyLog.v(previousFuture) { "Preparing for " + reasonSupplier.get() }

                        release(previousFuture, previousContext, reasonSupplier)
                        val myContext = previousContext.newInitialized(calledBy)
                        SyncInitiator.register(myContext)
                        MyServiceManager.registerReceiver(myContext.context)
                        myContext
                    }
                }

            return MyFutureContext(previousContext, future).also { futureContext ->
                future.onPostExecute { _, _ ->
                    futureContext.checkQueueExecutor(null, true)
                }
                if (future.isPending) {
                    future.execute(futureContext.previousContext)
                }
            }
        }

        private fun release(previousFuture: MyFutureContext, previousContext: MyContext, reason: Supplier<String>) {
            SyncInitiator.unregister(previousContext)
            TlsSniSocketFactory.forget()
            previousContext.save(reason)
            AsyncTaskLauncher.forget()
            ExceptionsCounter.forget()
            MyLog.forget()
            SharedPreferencesUtil.forget()
            FirstActivity.isFirstrun.set(true)
            previousContext.release(reason)
            // There is InterruptedException after above..., so we catch it below:
            DbUtils.waitMs(previousFuture, 10)
            MyLog.d(previousFuture, "Release completed, " + reason.get())
        }

        fun completed(myContext: MyContext): MyFutureContext {
            return MyFutureContext(MyContextEmpty.EMPTY, completedFuture(myContext))
        }

        private fun completedFuture(myContext: MyContext): AsyncResult<MyContext, MyContext> {
            val future = AsyncResult<MyContext, MyContext>("completedMyContext", AsyncEnum.QUICK_UI, true)
            future.result = Try.success(myContext)
            return future
        }

        fun startActivity(myContext: MyContext, intent: Intent?) {
            if (intent != null) {
                var launched = false
                if (myContext.isReady) {
                    try {
                        MyLog.d(TAG, "Start activity with intent:$intent")
                        myContext.context.startActivity(intent)
                        launched = true
                    } catch (e: AndroidRuntimeException) {
                        try {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            MyLog.d(TAG, "Start activity with intent (new task):$intent")
                            myContext.context.startActivity(intent)
                            launched = true
                        } catch (e2: Exception) {
                            MyLog.e(TAG, "Launching activity with Intent.FLAG_ACTIVITY_NEW_TASK flag", e)
                        }
                    } catch (e: SecurityException) {
                        MyLog.d(TAG, "Launching activity", e)
                    }
                }
                if (!launched) {
                    HelpActivity.startMe(myContext.context, true, HelpActivity.PAGE_LOGO)
                }
            }
        }
    }
}
