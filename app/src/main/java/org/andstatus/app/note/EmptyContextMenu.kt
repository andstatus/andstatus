package org.andstatus.app.note

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.MenuItem
import android.view.SubMenu
import android.view.View

class EmptyContextMenu: ContextMenu {

    override fun add(title: CharSequence?): MenuItem {
        TODO("Not yet implemented")
    }

    override fun add(titleRes: Int): MenuItem {
        TODO("Not yet implemented")
    }

    override fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence?): MenuItem {
        TODO("Not yet implemented")
    }

    override fun add(groupId: Int, itemId: Int, order: Int, titleRes: Int): MenuItem {
        TODO("Not yet implemented")
    }

    override fun addSubMenu(title: CharSequence?): SubMenu {
        TODO("Not yet implemented")
    }

    override fun addSubMenu(titleRes: Int): SubMenu {
        TODO("Not yet implemented")
    }

    override fun addSubMenu(groupId: Int, itemId: Int, order: Int, title: CharSequence?): SubMenu {
        TODO("Not yet implemented")
    }

    override fun addSubMenu(groupId: Int, itemId: Int, order: Int, titleRes: Int): SubMenu {
        TODO("Not yet implemented")
    }

    override fun addIntentOptions(groupId: Int, itemId: Int, order: Int, caller: ComponentName?, specifics: Array<out Intent>?, intent: Intent?, flags: Int, outSpecificItems: Array<out MenuItem>?): Int {
        TODO("Not yet implemented")
    }

    override fun removeItem(id: Int) {
        TODO("Not yet implemented")
    }

    override fun removeGroup(groupId: Int) {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun setGroupCheckable(group: Int, checkable: Boolean, exclusive: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setGroupVisible(group: Int, visible: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setGroupEnabled(group: Int, enabled: Boolean) {
        TODO("Not yet implemented")
    }

    override fun hasVisibleItems(): Boolean {
        TODO("Not yet implemented")
    }

    override fun findItem(id: Int): MenuItem {
        TODO("Not yet implemented")
    }

    override fun size(): Int {
        TODO("Not yet implemented")
    }

    override fun getItem(index: Int): MenuItem {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun performShortcut(keyCode: Int, event: KeyEvent?, flags: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun isShortcutKey(keyCode: Int, event: KeyEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun performIdentifierAction(id: Int, flags: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun setQwertyMode(isQwerty: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setHeaderTitle(titleRes: Int): ContextMenu {
        TODO("Not yet implemented")
    }

    override fun setHeaderTitle(title: CharSequence?): ContextMenu {
        TODO("Not yet implemented")
    }

    override fun setHeaderIcon(iconRes: Int): ContextMenu {
        TODO("Not yet implemented")
    }

    override fun setHeaderIcon(icon: Drawable?): ContextMenu {
        TODO("Not yet implemented")
    }

    override fun setHeaderView(view: View?): ContextMenu {
        TODO("Not yet implemented")
    }

    override fun clearHeader() {
        TODO("Not yet implemented")
    }

    class EmptyContextMenuInfo: ContextMenu.ContextMenuInfo

    companion object {
        val EMPTY = EmptyContextMenu()
        val EMPTY_INFO = EmptyContextMenuInfo()
    }
}