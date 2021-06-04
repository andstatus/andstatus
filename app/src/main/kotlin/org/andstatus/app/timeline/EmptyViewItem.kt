package org.andstatus.app.timeline

class EmptyViewItem private constructor() : ViewItem<EmptyViewItem> (isEmptyIn = true, updatedDate = 0) {

    companion object {
        val EMPTY: EmptyViewItem = EmptyViewItem()
    }
}

