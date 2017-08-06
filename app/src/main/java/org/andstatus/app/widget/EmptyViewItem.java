package org.andstatus.app.widget;

import java.util.Collection;
import java.util.Collections;

public class EmptyViewItem implements DuplicatesCollapsible {
    @Override
    public long getId() {
        return 0;
    }

    @Override
    public long getDate() {
        return 0;
    }

    @Override
    public DuplicationLink duplicates(DuplicatesCollapsible other) {
        return DuplicationLink.NONE;
    }

    @Override
    public boolean isCollapsed() {
        return false;
    }

    @Override
    public void collapse(DuplicatesCollapsible second) {
        // Empty
    }

    @Override
    public Collection<? extends DuplicatesCollapsible> getChildren() {
        return Collections.emptyList();
    }
}
