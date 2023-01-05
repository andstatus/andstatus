package org.andstatus.app.context

import io.vavr.control.Try
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.util.MyLog

class MyContextAction(
    val actionName: String,
    val action: (Try<MyContext>) -> Try<Unit>,
    val mainThread: Boolean = true
) {

    fun newTask(futureContext: MyFutureContext): AsyncResult<Unit, Unit> = newTask(futureContext, this)

    private fun newTask(futureContext: MyFutureContext, contextAction: MyContextAction) =
        AsyncResult<Unit, Unit>(
            "myContextAction_${futureContext.instanceTag}",
            AsyncEnum.QUICK_UI,
            false
        ).apply {
            if (contextAction.mainThread) {
                onPostExecute { _, _ ->
                    MyLog.v(this, "Before execution: $contextAction")
                    result = contextAction.action(futureContext.tryCurrent)
                    futureContext.onActionTaskExecuted(this, contextAction, result)
                }
            } else {
                doInBackground {
                    MyLog.v(this, "Before execution: $contextAction")
                    contextAction.action(futureContext.tryCurrent)
                }
                onPostExecute { _, _ ->
                    futureContext.onActionTaskExecuted(this, contextAction, result)
                }
            }
        }

    override fun toString(): String {
        return "MyContextAction:'$actionName', mainThread=$mainThread"
    }
}
