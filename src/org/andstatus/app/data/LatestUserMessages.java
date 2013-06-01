/**
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

package org.andstatus.app.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects {@link UserMsg} data (e.g. during timeline download) and allows to save it in bulk 
 * @author yvolk
 */
public class LatestUserMessages {
    private Map<Long, UserMsg> messages;
    public LatestUserMessages() {
        messages = new HashMap<Long, UserMsg>();
    }
    
    public Collection<UserMsg> getUserMessages() {
        return messages.values();
    }
    
    /**
     * Add information about new/updated message by the User
     */
    public void onNewUserMsg(UserMsg um_in) {
        // On different implementations see 
        // http://stackoverflow.com/questions/81346/most-efficient-way-to-increment-a-map-value-in-java
        UserMsg um = messages.get(um_in.getUserId());
        if (um == null) {
            um = um_in;
        } else {
            um.onNewMsg(um_in.getLastMsgId(), um_in.getLastMsgDate() );
        }
        messages.put(um.getUserId(), um);
    }
    
    /**
     * Persist all information into the database
     * @return true if succeeded for all entries
     */
    public boolean save() {
        boolean ok = true;
        for (UserMsg um : messages.values()) {
            if (!um.save()) {
                ok = false;
            }
        }
        return ok;
    }
}
