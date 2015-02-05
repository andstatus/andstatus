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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

/**
 * Execute command for each account
 * @author yvolk@yurivolkov.com
 */
public class CommandExecutorAllAccounts extends CommandExecutorStrategy {

    @Override
    public void execute() {
        for (MyAccount ma : MyContextHolder.get().persistentAccounts().collection()) {
            if ( !ma.isValidAndVerified()) {
                MyLog.v(this, "Account '" + ma.getAccountName() + "' skipped as no valid authenticated account");
                continue;
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
