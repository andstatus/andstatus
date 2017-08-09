package org.andstatus.app.widget;

public class EmptyViewItem implements TimelineViewItem {
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
