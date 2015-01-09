package org.andstatus.app.service;

import android.text.TextUtils;

import org.andstatus.app.data.DataInserter;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.MbTimelineItem;
import org.andstatus.app.util.MyLog;

import java.util.List;

public class CommandExecutorSearch extends CommandExecutorStrategy {

    @Override
    public void execute() {
        DataInserter di = new DataInserter(execContext);
        String searchQuery = execContext.getCommandData().getSearchQuery();
        if (TextUtils.isEmpty(searchQuery)) {
            MyLog.e(this,  "Search query is empty");
            execContext.getResult().incrementParseExceptions();
        } else {
            int limit = 200;
            List<MbTimelineItem> messages;
            try {
                messages = execContext.getMyAccount().getConnection().search(searchQuery, limit);
                for (MbTimelineItem item : messages) {
                    switch (item.getType()) {
                        case MESSAGE:
                            di.insertOrUpdateMsg(item.mbMessage);
                            break;
                        case USER:
                            di.insertOrUpdateUser(item.mbUser);
                            break;
                        default:
                            break;
                    }
                }
            } catch (ConnectionException e) {
                logConnectionException(e, "Search '" + searchQuery + "'");
            }
        }
    }
}
