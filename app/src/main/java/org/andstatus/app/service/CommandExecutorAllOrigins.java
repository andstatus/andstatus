package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;

/**
 * Execute command for one account of each origin
 * @author yvolk@yurivolkov.com
 */
class CommandExecutorAllOrigins extends CommandExecutorStrategy {

    @Override
    public void execute() {
        for (Origin origin : MyContextHolder.get().persistentOrigins().collection()) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().findFirstSucceededMyAccountByOriginId(origin.getId());
            if (!ma.isValidAndSucceeded()) {
                MyLog.v(this, "Origin '" + origin.getName() + "' skipped as no valid authenticated accounts");
                continue;
            } 
            switch (execContext.getCommandData().getCommand()) {
                case SEARCH_MESSAGE:
                    if (!origin.isInCombinedGlobalSearch()) {
                        MyLog.v(this, "Origin '" + origin.getName() + "' skipped from global search");
                        continue;
                    }
                    if (!ma.isGlobalSearchSupported()) {
                        MyLog.v(this, "Origin '" + origin.getName() + "' skipped as global search not supported");
                        continue;
                    }
                    break;
                case FETCH_TIMELINE:
                    if (execContext.getCommandData().getTimelineType() == TimelineType.PUBLIC &&
                            !origin.isInCombinedPublicReload()) {
                        MyLog.v(this, "Origin '" + origin.getName() + "' skipped from pulic timeline reload");
                        continue;
                    }
                    break;
                default:
                    break;
            }
            execContext.setMyAccount(ma);
            CommandExecutorStrategy.executeStep(execContext, this);
            if (isStopping()) {
                if ( !execContext.getResult().hasError()) {
                    execContext.getResult().incrementNumIoExceptions();
                    execContext.getResult().setMessage("Service is stopping");
                }
                break;
            }
        }
    }
}
