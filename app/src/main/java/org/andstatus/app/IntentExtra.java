/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.data.ParsedUri;

/**
 * Names of extras are used in the Intent-messaging 
 * (e.g. to notify Widget of new Messages)
 */
public enum IntentExtra{
    /**
     * This extra is used as a command to be executed by {@link MyService} and by
     * {@link MyAppWidgetProvider}. Value of this extra is a string code of CommandEnum
     */
    COMMAND("COMMAND_ENUM"),
    /**
     * Command parameter: long - ID of the Tweet (or Msg) / User / Origin
     */
    ITEMID("ITEMID"),
    COMMAND_RESULT("COMMAND_RESULT"),
    /**
     * {@link MyService.ServiceState}
     */
    SERVICE_STATE("SERVICE_STATE"),
    SERVICE_EVENT("SERVICE_EVENT"),
    /**
     * Text of the message/"tweet"
     */
    MESSAGE_TEXT("MESSAGE_TEXT"),
    MEDIA_URI("MEDIA_URI"),
    /**
     * Account name, see {@link MyAccount#getAccountName()}
     */
    ACCOUNT_NAME("ACCOUNT_NAME"),
    ORIGIN_NAME("ORIGIN_NAME"),
    ORIGIN_ID("ORIGIN_ID"),
    /**
     * Name of the preference to set
     */
    PREFERENCE_KEY("PREFERENCE_KEY"),
    PREFERENCE_VALUE("PREFERENCE_VALUE"),
    /**
     * Reply to
     */
    INREPLYTOID("INREPLYTOID"),
    /**
     * Recipient of a Direct message
     */
    RECIPIENTID("RECIPIENTID"),
    SEARCH_QUERY("SEARCH_QUERY"),
    /**
     * boolean. "true" means search in Internet, i.e. sending requests to Microblogging systems  
     */
    GLOBAL_SEARCH("GLOBAL_SEARCH"),
    /**
     * Selected User. E.g. the User whose messages we are seeing  
     */
    SELECTED_USERID("SELECTEDUSERID"),
    /**
     * Number of new tweets. Value is integer
     */
    NUMTWEETS("NUMTWEETS"),

    /** See {@link ParsedUri} */
    TIMELINE_URI("TIMELINE_URI"),
    
    /**
     * This extra is used to determine which timeline to show in
     * TimelineActivity Value is {@link MyDatabase.TimelineTypeEnum} 
     */
    TIMELINE_TYPE("TIMELINE_TYPE"),
    /**
     * Is the timeline combined in {@link TimelineActivity} 
     */
    TIMELINE_IS_COMBINED("TIMELINE_IS_COMBINED"),
    ROWS_LIMIT("ROWS_LIMIT"),
    POSITION_RESTORED("POSITION_RESTORED"),
    LOAD_ONE_MORE_PAGE("LOAD_ONE_MORE_PAGE"),
    REQUERY("REQUERY"),
    
    COMMAND_ID("COMMAND_ID"),
    LAST_EXECUTED_DATE("LAST_EXECUTED_DATE"),
    EXECUTION_COUNT("EXECUTION_COUNT"),
    RETRIES_LEFT("RETRIES_LEFT"),
    NUM_AUTH_EXCEPTIONS("NUM_AUTH_EXCEPTIONS"),
    NUM_IO_EXCEPTIONS("NUM_IO_EXCEPTIONS"),
    NUM_PARSE_EXCEPTIONS("NUM_PARSE_EXCEPTIONS"),
    ERROR_MESSAGE("ERROR_MESSAGE"),
    DOWNLOADED_COUNT("DOWNLOADED_COUNT"),
    IN_FOREGROUND("IN_FOREGROUND"),
    MANUALLY_LAUNCHED("MANUALLY_LAUNCHED"),
    IS_STEP("IS_STEP"),
    
    UNKNOWN("UNKNOWN");
    
    public final String key;
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