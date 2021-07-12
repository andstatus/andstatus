/*
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.FirstActivity
import org.andstatus.app.HelpActivity
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.TlsSniSocketFactory
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.ExceptionsCounter
import org.andstatus.app.os.NonUiThreadExecutor
import org.andstatus.app.syncadapter.SyncInitiator
import org.andstatus.app.util.Identified
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.Taggable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.function.UnaryOperator

/**
 * @author yvolk@yurivolkov.com
 */
class MyFutureContext private constructor(
    private val previousContext: MyContext,
    val future: CompletableFuture<MyContext>,
    private val identifiable: Identifiable = Identified(MyFutureContext::class)
) : Identifiable by identifiable {
    val createdAt = MyLog.uniqueCurrentTimeMS()
    override val instanceId = InstanceId.next()

    fun releaseNow(reason: Supplier<String>): MyFutureContext {
        val previousContext = getNow()
        release(previousContext, reason)
        return completed(previousContext)
    }

    fun isCompletedExceptionally(): Boolean {
        return future.isCompletedExceptionally()
    }

    fun isReady(): Boolean {
        return getNow().isReady
    }

    fun getNow(): MyContext {
        return tryNow().getOrElse(previousContext)
    }

    fun tryNow(): Try<MyContext> {
        return Try.success(previousContext).map { valueIfAbsent: MyContext? -> future.getNow(valueIfAbsent) }
    }

    fun whenSuccessAsync(consumer: Consumer<MyContext>, executor: Executor): MyFutureContext {
        return with { future: CompletableFuture<MyContext> ->
            future.whenCompleteAsync({ myContext: MyContext?, throwable: Throwable? ->
                MyLog.d(instanceTag, "whenSuccessAsync $myContext, $future")
                if (myContext != null) {
                    consumer.accept(myContext)
                }
            }, executor)
        }
    }

    fun whenSuccessOrPreviousAsync(executor: Executor, consumer: Consumer<MyContext>): MyFutureContext {
        return with { future: CompletableFuture<MyContext> ->
            future.whenCompleteAsync({ myContext: MyContext?, throwable: Throwable? ->
                consumer.accept(myContext ?: previousContext)
            }, executor)
        }
    }

    fun with(futures: UnaryOperator<CompletableFuture<MyContext>>): MyFutureContext {
        val healthyFuture: CompletableFuture<MyContext> = getHealthyFuture("(with)")
        val nextFuture = futures.apply(healthyFuture)
        MyLog.d(instanceTag, "with, after apply, next: $nextFuture")
        return MyFutureContext(previousContext, nextFuture)
    }

    private fun getHealthyFuture(calledBy: Any?): CompletableFuture<MyContext> {
        if (future.isDone()) {
            tryNow().onFailure { throwable: Throwable? ->
                MyLog.i(instanceTag, if (future.isCancelled()) "Previous initialization was cancelled"
                else "Previous initialization completed exceptionally, now called by " + calledBy, throwable)
            }
        }
        return if (future.isCompletedExceptionally()) completedFuture(previousContext) else future
    }

    fun tryBlocking(): Try<MyContext> {
        return Try.of { future.get() }
    }

    companion object {
        private val TAG: String = MyFutureContext::class.java.simpleName

        fun fromPrevious(previousFuture: MyFutureContext, calledBy: Any?): MyFutureContext {
            val future = previousFuture.getHealthyFuture(calledBy)
                    .thenApplyAsync(initializeMyContextIfNeeded(calledBy ?: previousFuture), NonUiThreadExecutor.INSTANCE)
            return MyFutureContext(previousFuture.getNow(), future)
        }

        private fun initializeMyContextIfNeeded(calledBy: Any?): UnaryOperator<MyContext> {
            return UnaryOperator { previousContext: MyContext ->
                val reason: String = if (!previousContext.isReady) {
                    "Context not ready"
                } else if (previousContext.isExpired) {
                    "Context expired"
                } else if (previousContext.isPreferencesChanged) {
                    "Preferences changed"
                } else {
                    ""
                }
                if (reason.isEmpty()) {
                    previousContext
                } else {
                    val reasonSupplier = Supplier {
                        ("Initialization: " + reason
                                + ", previous:" + Taggable.anyToTag(previousContext)
                                + " by " + Taggable.anyToTag(calledBy))
                    }
                    MyLog.v(TAG) { "Preparing for " + reasonSupplier.get() }
                    release(previousContext, reasonSupplier)
                    val myContext = previousContext.newInitialized(calledBy ?: previousContext)
                    SyncInitiator.register(myContext)
                    myContext
                }
            }
        }

        private fun release(previousContext: MyContext, reason: Supplier<String>) {
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
            DbUtils.waitMs(TAG, 10)
            MyLog.d(TAG, "Release completed, " + reason.get())
        }

        fun completed(myContext: MyContext): MyFutureContext {
            return MyFutureContext(MyContextEmpty.EMPTY, completedFuture(myContext))
        }

        private fun completedFuture(myContext: MyContext): CompletableFuture<MyContext> {
            val future = CompletableFuture<MyContext>()
            future.complete(myContext)
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
