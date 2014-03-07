package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.origin.Origin;

/**
 * Execute command for one account of each origin
 * @author yvolk@yurivolkov.com
 */
class CommandExecutorAllOrigins extends CommandExecutorBase {

    @Override
    public void execute() {
        for (Origin origin : MyContextHolder.get().persistentOrigins().collection()) {
            MyAccount acc = MyContextHolder.get().persistentAccounts().findFirstMyAccountByOriginId(origin.getId());
            if ( acc==null || acc.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
                commandData.getResult().incrementNumAuthExceptions();
            } else {
                getStrategy(commandData, acc).setParent(this).execute();
            }
            if (isStopping()) {
                setSoftErrorIfNotOk(commandData, false);
                break;
            }
        }
    }
}
