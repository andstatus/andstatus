/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

/**
 * ids in originating system
 */
public enum OidEnum {
    /** oid of this message */
    MSG_OID,
    /** If the message was reblogged by the User,
     * then oid of the "reblog" message,
     * else oid of the reblogged message (the first message which was reblogged)
     */
    REBLOG_OID,
    /** oid of this User */
    USER_OID
}
