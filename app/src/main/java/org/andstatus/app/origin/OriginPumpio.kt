/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;

import androidx.annotation.StringRes;

public class OriginPumpio extends Origin {
    public static final String ACCOUNT_PREFIX = "acct:";

    OriginPumpio(MyContext myContext, OriginType originType) {
        super(myContext, originType);
    }

    @Override
    public int alternativeTermForResourceId(@StringRes int resId) {
        int resIdOut;
        switch (resId) {
            case R.string.menu_item_destroy_reblog:
                resIdOut = R.string.menu_item_destroy_reblog_pumpio;
                break;
            case R.string.menu_item_reblog:
                resIdOut = R.string.menu_item_reblog_pumpio;
                break;
            case R.string.reblogged_by:
                resIdOut = R.string.reblogged_by_pumpio;
                break;
            default:
                resIdOut = resId;
                break;
        }
        return resIdOut;
    }
}
