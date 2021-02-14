/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.TriState;

import java.util.Optional;

/** Parameters that don't require reloading of the list */
public class LoadableListViewParameters {
    public final static LoadableListViewParameters EMPTY =
            new LoadableListViewParameters(TriState.UNKNOWN, 0, Optional.empty());
    public final TriState collapseDuplicates;
    public final long collapsedItemId;
    public final Optional<Origin> preferredOrigin;

    public static LoadableListViewParameters collapseDuplicates(boolean collapseDuplicates) {
        return collapseOneDuplicate(collapseDuplicates, 0);
    }

    public static LoadableListViewParameters collapseOneDuplicate(boolean collapseDuplicates, long collapsedItemId) {
        return new LoadableListViewParameters(TriState.fromBoolean(collapseDuplicates), collapsedItemId, Optional.empty());
    }

    public static LoadableListViewParameters fromOrigin(Origin preferredOrigin) {
        return new LoadableListViewParameters(TriState.UNKNOWN, 0, Optional.ofNullable(preferredOrigin));
    }

    public LoadableListViewParameters(TriState collapseDuplicates, long collapsedItemId, Optional<Origin> preferredOrigin) {
        this.collapseDuplicates = collapseDuplicates;
        this.collapsedItemId = collapsedItemId;
        this.preferredOrigin = preferredOrigin;
    }

    public boolean isViewChanging() {
        return collapseDuplicates.known || preferredOrigin.isPresent();
    }

}
