/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.account;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.andstatus.app.account.AccountName;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.origin.Origin.OriginEnum;
import org.andstatus.app.util.TriState;

public class MyAccountTest  extends InstrumentationTestCase {
    private static final String TAG = MyAccountTest.class.getSimpleName();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context targetContext = this.getInstrumentation().getTargetContext();
        if (targetContext == null) {
            Log.e(TAG, "targetContext is null.");
            throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
        }
        MyPreferences.initialize(targetContext, this);
    }

    public void testNewAccountCreation() {
       MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(AccountName.ORIGIN_SEPARATOR + OriginEnum.TWITTER.getName(), TriState.UNKNOWN);
       assertEquals("Creating account for '" + OriginEnum.TWITTER.getName() + "'", OriginEnum.TWITTER.getId(), builder.getAccount().getOriginId());
       assertEquals("Creating account for '" + OriginEnum.TWITTER.getName() + "'", "/twitter", builder.getAccount().getAccountName());
       builder = MyAccount.Builder.newOrExistingFromAccountName(AccountName.ORIGIN_SEPARATOR + OriginEnum.PUMPIO.getName(), TriState.UNKNOWN);
       assertEquals("Creating account for '" + OriginEnum.PUMPIO.getName() + "'", OriginEnum.PUMPIO.getId(), builder.getAccount().getOriginId());
       assertEquals("Creating account for '" + OriginEnum.PUMPIO.getName() + "'", "/pump.io", builder.getAccount().getAccountName());
    }
}
