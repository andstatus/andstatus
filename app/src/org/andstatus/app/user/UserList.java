/* 
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.user;

import android.app.ListActivity;
import android.os.Bundle;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.ParsedUri;

/**
 *  List of users for different contexts 
 *  e.g. "Users of the message", "Followers of my account(s)" etc.
 *  @author yvolk@yurivolkov.com
 */
public class UserList extends ListActivity {

    protected long mSelectedMessageId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mSelectedMessageId = ParsedUri.fromUri(getIntent().getData()).getMessageId();
        MyPreferences.setThemedContentView(this, R.layout.userlist);
        
    }

}
