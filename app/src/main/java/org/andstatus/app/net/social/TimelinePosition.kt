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

import android.net.Uri;

import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;

import java.util.Optional;

/**
 * Since introducing support for Pump.Io it appeared that 
 * Position in the Timeline and Id of the Note may be different things.
 * @author yvolk@yurivolkov.com
 */
public class TimelinePosition implements IsEmpty {
    public static final TimelinePosition EMPTY = new TimelinePosition("");
    private final String position;

    public static TimelinePosition of(String position) {
        if (StringUtil.isEmpty(position)) {
            return EMPTY;
        } else {
            return new TimelinePosition(position);
        }
    }

    private TimelinePosition(String position) {
        if (StringUtil.isEmpty(position)) {
            this.position = "";
        } else {
            this.position = position;
        }
    }

    public String getPosition() {
        return position;
    }

    public Optional<Uri> optUri() {
        return UriUtils.toDownloadableOptional(position);
    }

    @Override
    public String toString() {
        return position;
    }

    boolean isTemp() {
        return StringUtil.isTemp(position);
    }

    @Override
    public boolean isEmpty() {
        return StringUtil.isEmpty(position);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimelinePosition)) return false;
        return position.equals(((TimelinePosition) o).position);
    }

    @Override
    public int hashCode() {
        return position.hashCode();
    }
}
