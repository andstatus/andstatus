package org.andstatus.app.timeline;

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
