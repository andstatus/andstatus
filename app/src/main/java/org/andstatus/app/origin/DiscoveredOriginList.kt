/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.origin;

import android.os.Bundle;
import android.view.MenuItem;

import org.andstatus.app.R;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsListener;
import org.andstatus.app.service.MyServiceEventsReceiver;
import org.andstatus.app.service.MyServiceManager;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class DiscoveredOriginList extends OriginList implements MyServiceEventsListener {
    MyServiceEventsReceiver mServiceConnector = new MyServiceEventsReceiver(myContextHolder.getNow(), this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DiscoveredOrigins.get().isEmpty()) {
            mSwipeLayout.setRefreshing(true);
            manualSync();
        }
    }

    @Override
    public void onRefresh() {
        manualSync();
    }

    protected Iterable<Origin> getOrigins() {
        return DiscoveredOrigins.get();
    }

    private void manualSync() {
        MyServiceManager.setServiceAvailable();
        MyServiceManager.sendForegroundCommand(
                CommandData.newOriginCommand(CommandEnum.GET_OPEN_INSTANCES,
                        myContextHolder.getNow().origins().firstOfType(OriginType.GNUSOCIAL)
                        ));
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyServiceManager.setServiceAvailable();
        mServiceConnector.registerReceiver(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mServiceConnector.unregisterReceiver(this);
    }
    
    @Override
    public void onReceive(CommandData commandData, MyServiceEvent myServiceEvent) {
        if (MyServiceEvent.AFTER_EXECUTING_COMMAND.equals(myServiceEvent) 
                && CommandEnum.GET_OPEN_INSTANCES.equals(commandData.getCommand())) {
            fillList();
            mSwipeLayout.setRefreshing(false);
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.my_list_swipe;
    }
    
    @Override
    protected int getMenuResourceId() {
        return R.menu.discovered_origin_list;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sync_menu_item:
                manualSync();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
}
