package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.origin.Origin;

/**
 * Execute command for one account of each origin
 * @author yvolk@yurivolkov.com
 */
class CommandExecutorAllOrigins extends CommandExecutorStrategy {

    @Override
    public void execute() {
        for (Origin origin : MyContextHolder.get().persistentOrigins().collection()) {
            MyAccount acc = MyContextHolder.get().persistentAccounts().findFirstMyAccountByOriginId(origin.getId());
            if ( acc==null || acc.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
                execContext.getResult().incrementNumAuthExceptions();
                if (acc != null) {
                    execContext.getResult().setMessage(acc.getAccountName() + " account verification failed");
                }
            } else {
                execContext.setMyAccount(acc);
                CommandExecutorStrategy.executeStep(execContext, this);
            }
            if (isStopping()) {
                if ( !execContext.getResult().hasError()) {
                    execContext.getResult().setSoftErrorIfNotOk(false);
                    execContext.getResult().setMessage("Service is stopping");
                }
                break;
            }
        }
    }
}
