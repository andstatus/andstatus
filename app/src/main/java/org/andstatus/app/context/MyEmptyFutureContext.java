/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.support.annotation.NonNull;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyEmptyFutureContext extends MyFutureContext {

    public MyEmptyFutureContext(@NonNull MyContext previousContext) {
        super(previousContext);
    }

    @Override
    protected MyContext doInBackground2(Object... params) {
        return previousContext;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @NonNull
    @Override
    public MyContext getNow() {
        return previousContext;
    }

    @NonNull
    @Override
    public MyContext getBlocking() {
        return previousContext;
    }
}
