package org.andstatus.app.widget;

import org.andstatus.app.ViewItem;

public class EmptyViewItem implements ViewItem {
    public static final EmptyViewItem EMPTY = new EmptyViewItem();

    private EmptyViewItem() {
        // Empty
    }

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public long getDate() {
        return 0;
    }
}
