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
import kotlinx.coroutines.withContext
import org.andstatus.app.FirstActivity
import org.andstatus.app.HelpActivity
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.net.http.TlsSniSocketFactory
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.ExceptionsCounter
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.syncadapter.SyncInitiator
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.Taggable
import org.andstatus.app.util.TryUtils
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
) : Identifiable by future {

    val createdAt = MyLog.uniqueCurrentTimeMS
    private val actionTaskRef: AtomicReference<AsyncResult<Unit, Unit>?> = AtomicReference()

    fun onActionTaskExecuted(task: AsyncResult<Unit, Unit>, myContextAction: MyContextAction, result: Try<Unit>) {
        if (result.isSuccess) {
            MyLog.v(this, "Executed $myContextAction, result:${result.get()}")
        } else {
            MyLog.w(this, "Failed $myContextAction, result:${result.cause}")
        }
        checkQueueExecutor(task, false)
    }

    suspend fun release(reason: Supplier<String>) {
        val previousFuture = this
        return withContext(AsyncTaskLauncher.getExecutor(AsyncEnum.DEFAULT_POOL)) {
            if (!future.isFinished) {
                MyLog.i(previousFuture, "Will cancel running future, ${reason.get()}")
                future.cancel()
                delay(200)
            }
            while (!future.isFinished) {
                MyLog.i(previousFuture, "Previous future is running: ${previousFuture.future}")
                delay(200)
            }
            MyLog.i(previousFuture, "Previous future finished: ${previousFuture.future}")
            val myContext = getNow()
            release2(previousFuture, myContext, reason)
        }
    }

    val isReady: Boolean get() = getNow().isReady

    fun getNow(): MyContext {
        return future.result.getOrElse(previousContext)
    }

    /** Immediately get current state of MyContext initialization,
     * failure if not completed yet or if completed exceptionally */
    val tryCurrent: Try<MyContext> get() = future.result

    fun thenStartActivity(actionName: String, intent: Intent): MyFutureContext =
        then(actionName, true) { startActivity(it, intent) }

    fun then(actionName: String, mainThread: Boolean, consumer: (MyContext) -> Unit): MyFutureContext =
        thenTry(actionName, mainThread) { tryMyContext: Try<MyContext> -> tryMyContext.map { consumer(it) } }

    fun thenTry(
        actionName: String,
        mainThread: Boolean = true,
        action: (Try<MyContext>) -> Try<Unit>
    ): MyFutureContext {
        queue.put(MyContextAction(actionName, mainThread, action))
        checkQueueExecutor(null, false)
        return this
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
                            MyLog.d(this, "Launching: $action")
                            task.execute(Unit)
                        }
                    }
                }
            } else if (taskThatEnded === actionTaskRef.get()) {
                queue.poll()?.let { action ->
                    action.newTask(this)
                        .let { task ->
                            actionTaskRef.set(task)
                            MyLog.d(this, "Launching: $action")
                            task.execute(Unit)
                        }
                }
            }
        }
    }

    suspend fun getCompleted(): MyContext {
        return tryCompleted().getOrElse(getNow())
    }

    suspend fun tryCompleted(): Try<MyContext> {
        while (!future.isFinished) {
            delay(1000)
            MyLog.v(this, "Waiting for initialization to finish, ${future}")
        }
        return future.result
    }

    companion object {
        private val TAG: String = MyFutureContext::class.simpleName!!

        fun fromPrevious(previousFuture: MyFutureContext, calledByIn: Any?, duringUpgrade: Boolean): MyFutureContext =
            runBlocking {
                val previousContext = previousFuture.getNow()
                val calledBy: Any = calledByIn ?: previousContext
                var willInitialize = true;
                val reason: String = (
                    if (myContextHolder.onDeleteApplicationData) {
                        willInitialize = false
                        "On delete application data"
                    } else if (duringUpgrade) {
                        "During upgrade"
                    } else if (previousContext.state == MyContextState.UPGRADING) {
                        willInitialize = false
                        "Is upgrading"
                    } else if (previousContext.state == MyContextState.RESTORING) {
                        willInitialize = false
                        "Is restoring"
                    } else if (previousFuture.future.isRunning) {
                        willInitialize = false
                        "Previous is running"
                    } else if (!previousFuture.future.isFinished) {
                        willInitialize = false
                        "Previous didn't finish"
                    } else if (!previousContext.isReady) {
                        "Previous not ready"
                    } else if (previousContext.isExpired) {
                        "Previous expired"
                    } else if (previousContext.isPreferencesChanged) {
                        "Preferences changed"
                    } else {
                        willInitialize = false
                        "Is Ready"
                    }) +
                    ", previous in ${previousFuture.instanceTag}: " + previousContext +
                    ", called by: " + Taggable.anyToTag(calledBy) +
                    "\n${AsyncTaskLauncher.threadPoolInfo}"

                if (!willInitialize) {
                    MyLog.d(previousFuture, "ShouldInitialize: no, $reason")
                    return@runBlocking previousFuture
                }

                val future = AsyncResult<MyContext, MyContext>("futureContext", AsyncEnum.DEFAULT_POOL, false)
                    .also { future ->
                        future.doInBackground {
                            TryUtils.ofS {
                                MyLog.v(previousFuture) { "Preparing for initialization of ${future.instanceTag}" }
                                release2(previousFuture, previousContext) { "Initialization of ${future.instanceTag}" }
                                previousContext.newInstance(calledBy).also { myContext ->
                                    myContext.initialize()
                                    SyncInitiator.register(myContext)
                                    MyServiceManager.registerReceiver(myContext.context)
                                }
                            }.also {
                                MyLog.v(previousFuture) { "Initialization of ${future.instanceTag} result: $it" }
                            }
                        }
                    }

                return@runBlocking MyFutureContext(previousContext, future)
                    .also { futureContext ->
                        MyLog.d(futureContext, "ShouldInitialize: yes, $reason")

                        future.onPostExecute { _, _ ->
                            futureContext.checkQueueExecutor(null, true)
                        }
                        if (future.isPending) {
                            future.execute(futureContext.previousContext)
                        }
                    }
            }

        private suspend fun release2(
            previousFuture: MyFutureContext,
            previousContext: MyContext,
            reason: Supplier<String>
        ) {
            MyLog.d(previousFuture, "Release started, " + reason.get())
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
            delay(10)
            MyLog.d(previousFuture, "Release completed, " + reason.get())
        }

        fun completed(myContext: MyContext): MyFutureContext = MyFutureContext(myContext, completedFuture(myContext))

        private fun completedFuture(myContext: MyContext): AsyncResult<MyContext, MyContext> {
            val future = AsyncResult<MyContext, MyContext>("completedMyContext", AsyncEnum.QUICK_UI, true)
            future.result = Try.success(myContext)
            return future
        }

        fun startActivity(myContext: MyContext, intent: Intent?) {
            if (intent != null) {
                var launched = false
                if (myContext.isReady || myContext.state == MyContextState.UPGRADING) {
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
