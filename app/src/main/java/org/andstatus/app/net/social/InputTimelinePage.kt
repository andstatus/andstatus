/*
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import java.util.Collections;
import java.util.List;

import io.vavr.control.Try;

public class InputTimelinePage extends InputPage<AActivity> {
    public static final InputTimelinePage EMPTY = of(Collections.emptyList());
    public static final Try<InputTimelinePage> TRY_EMPTY = Try.success(EMPTY);

    public static InputTimelinePage of(List<AActivity> activities) {
        return new InputTimelinePage(AJsonCollection.EMPTY, activities);
    }

    public static InputTimelinePage of(AJsonCollection jsonCollection, List<AActivity> activities) {
        return new InputTimelinePage(jsonCollection, activities);
    }

    public InputTimelinePage(AJsonCollection jsonCollection, List<AActivity> activities) {
        super(jsonCollection, activities);
    }

    @Override
    public AActivity empty() {
        return AActivity.EMPTY;
    }
}
