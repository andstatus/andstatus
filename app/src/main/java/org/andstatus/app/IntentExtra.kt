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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.appwidget.MyAppWidgetProvider;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.service.MyService;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.timeline.meta.TimelineType;

/**
 * Names of extras are used in the Intent-messaging 
 * (e.g. to notify Widget of a new Notes)
 */
public enum IntentExtra{
    /**
     * This extra is used as a command to be executed by {@link MyService} and by
     * {@link MyAppWidgetProvider}. Value of this extra is a string code of CommandEnum
     */
    COMMAND("COMMAND_ENUM"),
    COMMAND_DESCRIPTION("COMMAND_DESCRIPTION"),
    REQUEST_CODE("REQUEST_CODE"),
    /**
     * Command parameter: long - ID of the Tweet (or Note) / Actor / Origin
     */
    ITEM_ID("ITEM_ID"),
    INSTANCE_ID("INSTANCE_ID"),
    COMMAND_RESULT("COMMAND_RESULT"),
    /**
     * {@link MyServiceState}
     */
    SERVICE_STATE("SERVICE_STATE"),
    SERVICE_EVENT("SERVICE_EVENT"),
    PROGRESS_TEXT("PROGRESS_TEXT"),
    /** Text of the note/"tweet" */
    NOTE_TEXT("NOTE_TEXT"),
    MEDIA_URI("MEDIA_URI"),
    /** Account name, see {@link MyAccount#getAccountName()} */
    ACCOUNT_NAME("ACCOUNT_NAME"),
    /** Selected Actor. E.g. the Actor whose notes we are seeing */
    ACTOR_ID("ACTOR_ID"),
    USERNAME("ACTOR_NAME"),
    ORIGIN_ID("ORIGIN_ID"),
    ORIGIN_NAME("ORIGIN_NAME"),
    ORIGIN_TYPE("ORIGIN_TYPE"),
    /** @see org.andstatus.app.view.MyContextMenu#MENU_GROUP_ACTOR MENU_GROUP_... */
    MENU_GROUP("MENU_GROUP"),
    /**
     * Name of the preference to set
     */
    PREFERENCE_KEY("PREFERENCE_KEY"),
    PREFERENCE_VALUE("PREFERENCE_VALUE"),
    /**
     * Reply to
     */
    IN_REPLY_TO_ID("IN_REPLY_TO_ID"),
    /**
     * Recipient of a (usually private) note
     */
    RECIPIENT_ID("RECIPIENT_ID"),
    SEARCH_QUERY("SEARCH_QUERY"),
    /**
     * Number of new tweets. Value is integer
     */
    NUM_TWEETS("NUM_TWEETS"),

    /** See {@link ParsedUri} */
    MATCHED_URI("MATCHED_URI"),
    
    /**
     * This extra is used to determine which timeline to show in
     * TimelineActivity Value is {@link TimelineType}
     */
    TIMELINE_TYPE("TIMELINE_TYPE"),
    TIMELINE_ID("TIMELINE_ID"),
    SELECTABLE_ENUM("SELECTABLE_ENUM"),
    /**
     * Is the timeline combined
     */
    TIMELINE_IS_COMBINED("TIMELINE_IS_COMBINED"),
    ROWS_LIMIT("ROWS_LIMIT"),
    POSITION_RESTORED("POSITION_RESTORED"),
    WHICH_PAGE("WHICH_PAGE"),

    COMMAND_ID("COMMAND_ID"),
    CREATED_DATE("UPDATED_DATE"),
    LAST_EXECUTED_DATE("LAST_EXECUTED_DATE"),
    EXECUTION_COUNT("EXECUTION_COUNT"),
    FINISH("FINISH"),
    RETRIES_LEFT("RETRIES_LEFT"),
    NUM_AUTH_EXCEPTIONS("NUM_AUTH_EXCEPTIONS"),
    NUM_IO_EXCEPTIONS("NUM_IO_EXCEPTIONS"),
    NUM_PARSE_EXCEPTIONS("NUM_PARSE_EXCEPTIONS"),
    ERROR_MESSAGE("ERROR_MESSAGE"),
    DOWNLOADED_COUNT("DOWNLOADED_COUNT"),
    IN_FOREGROUND("IN_FOREGROUND"),
    MANUALLY_LAUNCHED("MANUALLY_LAUNCHED"),
    IS_STEP("IS_STEP"),
    CHAINED_REQUEST("CHAINED_REQUEST"),
    COLLAPSE_DUPLICATES("COLLAPSE_DUPLICATES"),
    INITIAL_ACCOUNT_SYNC("INITIAL_ACCOUNT_SYNC"),
    SYNC("SYNC"),

    CHECK_DATA("CHECK_DATA"),
    CHECK_SCOPE("CHECK_SCOPE"),
    FULL_CHECK("FULL_CHECK"),
    COUNT_ONLY("COUNT_ONLY"),

    SETTINGS_GROUP("SETTINGS_GROUP"),

    UNKNOWN("UNKNOWN");
    
    public final String key;
    IntentExtra(String keySuffix) {
        key = ClassInApplicationPackage.PACKAGE_NAME + "." + keySuffix;
    }
}