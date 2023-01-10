package org.andstatus.app.context

import io.vavr.control.Try
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.util.MyLog

class MyContextAction(
    val actionName: String,
    val mainThread: Boolean = true,
    val action: (Try<MyContext>) -> Try<Unit>
) {

    fun newTask(futureContext: MyFutureContext): AsyncResult<Unit, Unit> = newTask(futureContext, this)

    private fun newTask(futureContext: MyFutureContext, contextAction: MyContextAction) =
        AsyncResult<Unit, Unit>(
            "action$actionName",
            AsyncEnum.DEFAULT_POOL,
            false
        ).apply {
            if (contextAction.mainThread) {
                onPostExecute { _, _ ->
                    MyLog.v(this, "Before execution: ${contextAction.actionName}")
                    result = contextAction.action(futureContext.tryCurrent)
                    futureContext.onActionTaskExecuted(this, contextAction, result)
                }
            } else {
                doInBackground {
                    MyLog.v(this, "Before execution: ${contextAction.actionName}")
                    contextAction.action(futureContext.tryCurrent)
                }
                onPostExecute { _, _ ->
                    futureContext.onActionTaskExecuted(this, contextAction, result)
                }
            }
            afterFinish {
                result.onSuccess {
                    MyLog.d(this, "Success: ${contextAction.actionName}: $it")
                }.onFailure {
                    MyLog.d(this, "Failure: ${contextAction.actionName}: $it")
                }
            }
        }

    override fun toString(): String {
        return "$actionName, mainThread=$mainThread"
    }
}
