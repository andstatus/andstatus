package org.andstatus.app.timeline;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class EmptyViewItem extends ViewItem<EmptyViewItem> {
    public static final EmptyViewItem EMPTY = new EmptyViewItem();

    private EmptyViewItem() {
        super(true, DATETIME_MILLIS_NEVER);
    }
}
