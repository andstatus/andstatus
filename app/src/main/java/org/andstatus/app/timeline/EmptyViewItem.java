package org.andstatus.app.timeline;

public class EmptyViewItem extends ViewItem<EmptyViewItem> {
    public static final EmptyViewItem EMPTY = new EmptyViewItem();

    private EmptyViewItem() {
        super(true);
    }
}
