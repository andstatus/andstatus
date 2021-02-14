/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.account;

/**
 * Interface that allows to read the {@link MyAccount}'s persistent data (including account's connection data) 
 */
public interface AccountDataReader {

    boolean dataContains(String key);

    default String getDataString(String key) {
        return getDataString(key, "");
    }

    /**
     * @param key Key Name
     * @param defValue Default value
     * @return Returns null only in case defValue is null
     */
    String getDataString(String key, String defValue);

    int getDataInt(String key, int defValue);    
}
