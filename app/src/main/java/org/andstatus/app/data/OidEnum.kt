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
package org.andstatus.app.data

/**
 * ids in originating system
 */
enum class OidEnum {
    /** oid of this note  */
    NOTE_OID,

    /** If a note was reblogged by an Actor, then this is oid of an "announce" ("reblog") note,
     * else oid of the reblogged note (the first note which was reblogged)
     */
    REBLOG_OID,

    /** oid of this Actor  */
    ACTOR_OID, ACTIVITY_OID
}