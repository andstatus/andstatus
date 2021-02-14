/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.util

import android.app.Activity
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.annotation.IdRes

/**
 * @author yvolk@yurivolkov.com
 */
object MyCheckBox {
    private val EMPTY_ON_CHECKED_CHANGE_LISTENER: CompoundButton.OnCheckedChangeListener? = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
        // Empty;
    }

    fun isChecked(activity: Activity?, @IdRes checkBoxViewId: Int, defaultValue: Boolean): Boolean {
        val view = activity.findViewById<View?>(checkBoxViewId)
        return if (view == null || !CheckBox::class.java.isAssignableFrom(view.javaClass)) {
            defaultValue
        } else (view as CheckBox).isChecked
    }

    fun setEnabled(activity: Activity?, @IdRes viewId: Int, checked: Boolean): Boolean {
        return set(activity, viewId, checked, true)
    }

    fun setEnabled(parentView: View?, @IdRes viewId: Int, checked: Boolean): Boolean {
        return set(parentView, viewId, checked, EMPTY_ON_CHECKED_CHANGE_LISTENER)
    }

    operator fun set(activity: Activity?, @IdRes viewId: Int, checked: Boolean, enabled: Boolean): Boolean {
        return set(activity, viewId, checked, if (enabled) EMPTY_ON_CHECKED_CHANGE_LISTENER else null)
    }

    operator fun set(activity: Activity?, @IdRes viewId: Int, checked: Boolean,
                     onCheckedChangeListener: CompoundButton.OnCheckedChangeListener?): Boolean {
        return set(activity.findViewById(viewId), checked, onCheckedChangeListener)
    }

    operator fun set(parentView: View?, @IdRes viewId: Int, checked: Boolean,
                     onCheckedChangeListener: CompoundButton.OnCheckedChangeListener?): Boolean {
        return set(parentView.findViewById(viewId), checked, onCheckedChangeListener)
    }

    /**
     * @return true if succeeded
     */
    operator fun set(checkBoxIn: View?, checked: Boolean,
                     onCheckedChangeListener: CompoundButton.OnCheckedChangeListener?): Boolean {
        val success = checkBoxIn != null && CheckBox::class.java.isAssignableFrom(checkBoxIn.javaClass)
        if (success) {
            val checkBox = checkBoxIn as CheckBox?
            checkBox.setOnCheckedChangeListener(null)
            checkBox.setChecked(checked)
            checkBox.setEnabled(onCheckedChangeListener != null)
            if (onCheckedChangeListener != null && onCheckedChangeListener !== EMPTY_ON_CHECKED_CHANGE_LISTENER) {
                checkBox.setOnCheckedChangeListener(onCheckedChangeListener)
            }
        }
        return success
    }
}