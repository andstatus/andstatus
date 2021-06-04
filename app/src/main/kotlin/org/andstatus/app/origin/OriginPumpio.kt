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
package org.andstatus.app.origin

import androidx.annotation.StringRes
import org.andstatus.app.R
import org.andstatus.app.context.MyContext

class OriginPumpio internal constructor(myContext: MyContext, originType: OriginType) : Origin(myContext, originType) {
    override fun alternativeTermForResourceId(@StringRes resId: Int): Int {
        val resIdOut: Int
        resIdOut = when (resId) {
            R.string.menu_item_destroy_reblog -> R.string.menu_item_destroy_reblog_pumpio
            R.string.menu_item_reblog -> R.string.menu_item_reblog_pumpio
            R.string.reblogged_by -> R.string.reblogged_by_pumpio
            else -> resId
        }
        return resIdOut
    }

    companion object {
        val ACCOUNT_PREFIX: String = "acct:"
    }
}
