/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
     * This extra is used as a command to perform by MyService and by
     * MyAppWidgetProvider. Value of this extra is a string code of CommandEnum
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
    EXTRA_SERVICE_EVENT("SERVICE_EVENT"),
    /**
     * Text of the message/"tweet"
     */
    EXTRA_MESSAGE_TEXT("MESSAGE_TEXT"),
    EXTRA_MEDIA_URI("MEDIA_URI"),
    /**
     * Account name, see {@link MyAccount#getAccountName()}
     */
    EXTRA_ACCOUNT_NAME("ACCOUNT_NAME"),
    EXTRA_ORIGIN_NAME("ORIGIN_NAME"),
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
    EXTRA_SEARCH_QUERY("SEARCH_QUERY"),
    /**
     * boolean. "true" means search in Internet, i.e. sending requests to Microblogging systems  
     */
    EXTRA_GLOBAL_SEARCH("GLOBAL_SEARCH"),
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
    EXTRA_ROWS_LIMIT("ROWS_LIMIT"),
    EXTRA_POSITION_RESTORED("POSITION_RESTORED"),
    EXTRA_LOAD_ONE_MORE_PAGE("LOAD_ONE_MORE_PAGE"),
    EXTRA_REQUERY("REQUERY"),
    ORIGIN_ID("ORIGIN_ID"),
    
    EXTRA_COMMAND_ID("COMMAND_ID"),
    EXTRA_LAST_EXECUTED_DATE("LAST_EXECUTED_DATE"),
    EXTRA_EXECUTION_COUNT("EXECUTION_COUNT"),
    EXTRA_RETRIES_LEFT("RETRIES_LEFT"),
    EXTRA_NUM_AUTH_EXCEPTIONS("NUM_AUTH_EXCEPTIONS"),
    EXTRA_NUM_IO_EXCEPTIONS("NUM_IO_EXCEPTIONS"),
    EXTRA_NUM_PARSE_EXCEPTIONS("NUM_PARSE_EXCEPTIONS"),
    EXTRA_ERROR_MESSAGE("ERROR_MESSAGE"),
    EXTRA_DOWNLOADED_COUNT("DOWNLOADED_COUNT"),
    EXTRA_IN_FOREGROUND("IN_FOREGROUND"),
    EXTRA_MANUALLY_LAUCHED("MANUALLY_LAUCHED"),
    
    UNKNOWN("UNKNOWN");
    
    public final String key;
    /**
     * This prefix should be the same as in the AndroidManifest
     * It is used for all actions of this application
     */
    public static final String MY_ACTION_PREFIX = ClassInApplicationPackage.PACKAGE_NAME + ".action.";
    
    private IntentExtra(String keySuffix) {
        key = ClassInApplicationPackage.PACKAGE_NAME + "." + keySuffix;
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