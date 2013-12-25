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

package org.andstatus.app;

/**
 * Names of extras are used in the Intent-messaging 
 * (e.g. to notify Widget of new Messages)
 */
public enum IntentExtra{
    /**
     * This extra is used as a command to perform by MyService and
     * MyAppWidgetProvider Value of this extra is string code of
     * CommandEnum (not serialized enum !)
     */
    EXTRA_MSGTYPE("MSGTYPE"),
    /**
     * Command parameter: long - ID of the Tweet (or Msg)
     */
    EXTRA_ITEMID("ITEMID"),
    EXTRA_COMMAND_RESULT("COMMAND_RESULT"),
    /**
     * ({@link MyService.ServiceState}
     */
    EXTRA_SERVICE_STATE("SERVICE_STATE"),
    /**
     * Text of the status message
     */
    EXTRA_STATUS("STATUS"),
    /**
     * Account name, see {@link MyAccount#getAccountName()}
     */
    EXTRA_ACCOUNT_NAME("ACCOUNT_NAME"),
    EXTRA_ORIGIN_NAME("ORIGIN_NAME"),
    /**
     * Do we need to show the account?
     */
    EXTRA_SHOW_ACCOUNT("SHOW_ACCOUNT"),
    /**
     * Name of the preference to set
     */
    EXTRA_PREFERENCE_KEY("PREFERENCE_KEY"),
    EXTRA_PREFERENCE_VALUE("PREFERENCE_VALUE"),
    /**
     * Reply to
     */
    EXTRA_INREPLYTOID("INREPLYTOID"),
    /**
     * Recipient of a Direct message
     */
    EXTRA_RECIPIENTID("RECIPIENTID"),
    /**
     * Selected User. E.g. the User whose messages we are seeing  
     */
    EXTRA_SELECTEDUSERID("SELECTEDUSERID"),
    /**
     * Number of new tweets. Value is integer
     */
    EXTRA_NUMTWEETS("NUMTWEETS"),
    /**
     * This extra is used to determine which timeline to show in
     * TimelineActivity Value is {@link MyDatabase.TimelineTypeEnum} 
     */
    EXTRA_TIMELINE_TYPE("TIMELINE_TYPE"),
    /**
     * Is the timeline combined in {@link TimelineActivity} 
     */
    EXTRA_TIMELINE_IS_COMBINED("TIMELINE_IS_COMBINED"),
    ORIGIN_ID("ORIGIN_ID"),
    UNKNOWN("UNKNOWN");
    
    public final String key;
    
    private IntentExtra(String keySuffix) {
        key = this.getClass().getPackage().getName() + "." + keySuffix;
    }

    public static IntentExtra fromKey(String key) {
        for (IntentExtra item : IntentExtra.values()) {
            if (item.key.contentEquals(key)) {
                return item;
            }
        }
        return UNKNOWN;
    }        
}