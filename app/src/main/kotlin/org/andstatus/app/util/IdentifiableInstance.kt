/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.util

/**
 * Helps easier distinguish instances of a class e.g. in log messages
 * @author yvolk@yurivolkov.com
 */
interface IdentifiableInstance : TaggedClass {
    val instanceId: Long

    val instanceIdString: String get() = instanceId.toString()

    val instanceTag: String
        get() {
            val className = classTag()
            val idString = instanceIdString
            val maxClassNameLength = MyLog.MAX_TAG_LENGTH - idString.length
            val classNameTruncated =
                if (className.length > maxClassNameLength) className.substring(0, maxClassNameLength) else className
            return classNameTruncated + idString
        }
}
