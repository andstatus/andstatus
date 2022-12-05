/*
 * Copyright (C) 2022 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.ActionProvider
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.SubMenu
import android.view.View

/**
 * @author yvolk@yurivolkov.com
 */
class MenuItemStub(itemId: Int) : MenuItem {
    private val mItemId: Int = itemId

    @Volatile
    private var mCalled = false
    fun called(): Boolean {
        return mCalled
    }

    override fun getItemId(): Int {
        mCalled = true
        return mItemId
    }

    override fun getGroupId(): Int {
        return 0
    }

    override fun getOrder(): Int {
        return 0
    }

    override fun setTitle(title: CharSequence?): MenuItem {
        return this
    }

    override fun setTitle(title: Int): MenuItem {
        return this
    }

    override fun getTitle(): CharSequence? {
        return null
    }

    override fun setTitleCondensed(title: CharSequence?): MenuItem {
        return this
    }

    override fun getTitleCondensed(): CharSequence? {
        return null
    }

    override fun setIcon(icon: Drawable?): MenuItem {
        return this
    }

    override fun setIcon(iconRes: Int): MenuItem {
        return this
    }

    override fun getIcon(): Drawable? {
        return null
    }

    override fun setIntent(intent: Intent?): MenuItem {
        return this
    }

    override fun getIntent(): Intent? {
        return null
    }

    override fun setShortcut(numericChar: Char, alphaChar: Char): MenuItem {
        return this
    }

    override fun setNumericShortcut(numericChar: Char): MenuItem {
        return this
    }

    override fun getNumericShortcut(): Char {
        return '0'
    }

    override fun setAlphabeticShortcut(alphaChar: Char): MenuItem {
        return this
    }

    override fun getAlphabeticShortcut(): Char {
        return 'a'
    }

    override fun setCheckable(checkable: Boolean): MenuItem {
        return this
    }

    override fun isCheckable(): Boolean {
        return false
    }

    override fun setChecked(checked: Boolean): MenuItem {
        return this
    }

    override fun isChecked(): Boolean {
        return false
    }

    override fun setVisible(visible: Boolean): MenuItem {
        return this
    }

    override fun isVisible(): Boolean {
        return false
    }

    override fun setEnabled(enabled: Boolean): MenuItem {
        return this
    }

    override fun isEnabled(): Boolean {
        return false
    }

    override fun hasSubMenu(): Boolean {
        return false
    }

    override fun getSubMenu(): SubMenu? {
        return null
    }

    override fun setOnMenuItemClickListener(menuItemClickListener: MenuItem.OnMenuItemClickListener?): MenuItem {
        return this
    }

    override fun getMenuInfo(): ContextMenuInfo? {
        return null
    }

    override fun setShowAsAction(actionEnum: Int) {}
    override fun setShowAsActionFlags(actionEnum: Int): MenuItem {
        return this
    }

    override fun setActionView(view: View?): MenuItem {
        return this
    }

    override fun setActionView(resId: Int): MenuItem {
        return this
    }

    override fun getActionView(): View? {
        return null
    }

    override fun setActionProvider(actionProvider: ActionProvider?): MenuItem {
        return this
    }

    override fun getActionProvider(): ActionProvider? {
        return null
    }

    override fun expandActionView(): Boolean {
        return false
    }

    override fun collapseActionView(): Boolean {
        return false
    }

    override fun isActionViewExpanded(): Boolean {
        return false
    }

    override fun setOnActionExpandListener(listener: MenuItem.OnActionExpandListener?): MenuItem {
        return this
    }

}
