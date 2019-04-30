/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.StringUtils;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;

public class AccountNameTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
    }

    @Test
    public void preserveInputValues() {
        assertForOneOrigin(Origin.EMPTY);
        assertForOneOrigin(demoData.getPumpioConversationOrigin());
        assertForOneOrigin(demoData.getGnuSocialOrigin());
    }

    public void assertForOneOrigin(Origin origin) {
        assertInputValues(origin, "");
        assertInputValues(origin, "user1");
        assertInputValues(origin, "user1" + (
                origin.hasHost()
                        ? "@" + origin.getHost()
                        : ""
        ));
    }

    public void assertInputValues(Origin origin, String uniqueName) {
        AccountName accountName = AccountName.fromOriginAndUniqueName(origin, uniqueName);
        assertEquals(origin, accountName.origin);
        String expected = StringUtils.isEmpty(uniqueName)
                ? ""
                : ( uniqueName.contains("@")
                    ? uniqueName
                    : uniqueName + (origin.hasHost()
                        ? "@" + origin.getHost()
                        : "")
                );
        assertEquals(expected, accountName.getUniqueName());
    }
}
