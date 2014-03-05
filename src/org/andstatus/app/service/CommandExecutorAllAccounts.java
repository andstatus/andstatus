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

package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

public class CommandExecutorAllAccounts extends CommandExecutorBase {

    @Override
    public void execute() {
        MyLog.d(this, "Executing " + commandData);

        if (commandData.getAccount() == null) {
            for (MyAccount acc : MyContextHolder.get().persistentAccounts().collection()) {
                executeForAccount(commandData, acc);
                if (isStopping()) {
                    setSoftErrorIfNotOk(commandData, false);
                    break;
                }
            }
        } else {
            executeForAccount(commandData, commandData.getAccount());
        }
    }
    
    private void executeForAccount(CommandData commandData, MyAccount acc) {
        if ( acc.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
            commandData.getResult().incrementNumAuthExceptions();
        } else {
            getStrategy(commandData).setCommandData(commandData).setMyAccount(acc).setParent(this).execute();
        }
    }

    private CommandExecutorStrategy getStrategy(CommandData commandData) {
        Class<? extends CommandExecutorStrategy> oneCommandExecutorClass;
        switch (commandData.getCommand()) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
            case SEARCH_MESSAGE:
                oneCommandExecutorClass = CommandExecutorLoadTimeline.class;
                break;
            default:
                oneCommandExecutorClass = CommandExecutorOther.class;
                break;
        }
        CommandExecutorStrategy strategy = null;
        try {
            strategy = oneCommandExecutorClass.newInstance();
        } catch (InstantiationException e) {
            MyLog.e(this, e);
        } catch (IllegalAccessException e) {
            MyLog.e(this, e);
        }
        return strategy;
    }
}
