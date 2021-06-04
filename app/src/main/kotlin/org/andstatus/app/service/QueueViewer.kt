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
package org.andstatus.app.service

import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.MenuItem
import android.view.View
import io.vavr.control.Try
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.timeline.WhichPage
import org.andstatus.app.util.MyLog
import java.util.*
import java.util.function.Consumer

class QueueViewer : LoadableListActivity<QueueData>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.my_list
        super.onCreate(savedInstanceState)
    }

    override fun newSyncLoader(args: Bundle?): SyncLoader<QueueData> {
        return object : SyncLoader<QueueData>() {
            override fun load(publisher: ProgressPublisher?) {
                val queueTypes = arrayOf<QueueType>(QueueType.CURRENT, QueueType.SKIPPED, QueueType.RETRY, QueueType.ERROR)
                for (queueType in queueTypes) {
                    val oneQueue = myContext.queues[queueType]
                    for (commandData in oneQueue.queue) {
                        items.add(QueueData.getNew(queueType, commandData))
                    }
                }
                Collections.sort(items)
            }
        }
    }

    override fun newListAdapter(): BaseTimelineAdapter<QueueData> {
        return QueueViewerAdapter(this, getLoaded().getList() as MutableList<QueueData>)
    }

    private var queueData: QueueData? = null
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.commands_queue, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.clear_the_queue -> AsyncTaskLauncher.execute<QueueViewer?, Void>(this,
                    { activity: QueueViewer? ->  MyContextHolder.myContextHolder.getBlocking().queues.clear() },
                    { activity: QueueViewer? -> Consumer { r: Try<Void> -> activity?.showList(WhichPage.CURRENT) } })
            else -> return super.onOptionsItemSelected(item)
        }
        return false
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        queueData = getListAdapter().getItem(v)
        val inflater = menuInflater
        inflater.inflate(R.menu.queue_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val data = queueData
        return if (data == null) {
            super.onContextItemSelected(item)
        } else when (item.getItemId()) {
            R.id.menuItemShare -> {
                share(data)
                true
            }
            R.id.menuItemResend -> {
                data.commandData.resetRetries()
                MyServiceManager.sendManualForegroundCommand(data.commandData)
                true
            }
            R.id.menuItemDelete -> {
                AsyncTaskLauncher.execute<QueueViewer?, Void>(this,
                        { activity: QueueViewer? ->
                            MyContextHolder.myContextHolder.getBlocking().queues.deleteCommand(data.commandData)
                        },
                        { activity: QueueViewer? -> Consumer { r: Try<Void> -> activity?.showList(WhichPage.CURRENT) } })
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun share(queueData: QueueData) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, queueData.toSharedSubject())
        intent.putExtra(Intent.EXTRA_TEXT, queueData.toSharedText())
        startActivity(Intent.createChooser(intent, getText(R.string.menu_item_share)))
    }

    override fun onReceive(commandData: CommandData, myServiceEvent: MyServiceEvent) {
        if (MyServiceEvent.ON_STOP == myServiceEvent) {
            MyLog.v(this, "On service stop")
            showList(WhichPage.CURRENT)
        }
    }
}
