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

package org.andstatus.app.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 * See also http://www.javacreed.com/sorting-a-copyonwritearraylist/
 */
public class CollectionsUtil {

    private CollectionsUtil() {
        // Empty
    }

    public static <T extends Comparable<? super T>> void sort(List<T> list) {
        List<T> sortableList = new ArrayList<>(list);
        Collections.sort(sortableList);
        list.clear();
        list.addAll(sortableList);
    }

    public static int compareCheckbox(boolean lhs, boolean rhs) {
        int result = lhs == rhs ? 0 : lhs ? 1 : -1;
        return 0 - result;
    }

}
