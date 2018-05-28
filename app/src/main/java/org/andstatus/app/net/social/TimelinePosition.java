/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;

/**
 * Since introducing support for Pump.Io it appeared that 
 * Position in the Timeline and Id of the Note may be different things.
 * @author yvolk@yurivolkov.com
 */
public class TimelinePosition {
    public static final TimelinePosition EMPTY = new TimelinePosition("");
    private final String position;

    public TimelinePosition(String position) {
        if (StringUtils.isEmpty(position)) {
            this.position = "";
        } else {
            this.position = position;
        }
    }

    public String getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return position;
    }

    public boolean isTemp() {
        return nonEmpty() && position.startsWith(UriUtils.TEMP_OID_PREFIX);
    }

    public boolean isEmpty() {
        return StringUtils.isEmpty(position);
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof TimelinePosition)) {
            return false;
        }
        return hashCode() == o.hashCode();
    }

    @Override
    public int hashCode() {
        return position.hashCode();
    }
}
