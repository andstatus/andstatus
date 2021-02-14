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
package org.andstatus.app.view

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyTheme
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder

/**
 * @author yvolk@yurivolkov.com
 */
open class SelectorDialog : DialogFragment() {
    var toolbar: Toolbar? = null
    var listView: ListView? = null
    private val mLayoutId = R.layout.my_list_dialog
    protected var myContext: MyContext? = MyContextHolder.Companion.myContextHolder.getNow()
    private var resultReturned = false
    fun setRequestCode(requestCode: ActivityRequestCode?): Bundle? {
        val args = Bundle()
        args.putInt(IntentExtra.REQUEST_CODE.key, requestCode.id)
        arguments = args
        return args
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(activity,
                MyTheme.getThemeId(activity, MyTheme.getThemeName(activity)))
        MyTheme.applyStyles(dialog.context, true)
        return dialog
    }

    fun getListView(): ListView? {
        return listView
    }

    protected fun setListAdapter(adapter: MySimpleAdapter?) {
        if (listView != null) {
            listView.setAdapter(adapter)
        }
    }

    fun getListAdapter(): MySimpleAdapter? {
        return if (listView != null) {
            listView.getAdapter() as MySimpleAdapter
        } else null
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(mLayoutId, container, false)
        toolbar = view.findViewById<View?>(R.id.my_action_bar) as Toolbar
        listView = view.findViewById<View?>(android.R.id.list) as ListView
        return view
    }

    fun setTitle(@StringRes resId: Int) {
        if (toolbar != null) {
            toolbar.setTitle(resId)
        }
    }

    fun setTitle(title: String?) {
        if (toolbar != null) {
            toolbar.setTitle(title)
        }
    }

    protected fun returnSelected(selectedData: Intent?) {
        var returnResult = false
        if (!resultReturned) {
            resultReturned = true
            returnResult = true
        }
        dismiss()
        if (returnResult) {
            val activity: Activity? = activity
            if (activity != null) {
                (activity as MyActivity?).onActivityResult(
                        myGetArguments().getInt(IntentExtra.REQUEST_CODE.key),
                        Activity.RESULT_OK,
                        selectedData
                )
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        if (!resultReturned) {
            resultReturned = true
            val activity: Activity? = activity
            if (activity != null) {
                (activity as MyActivity?).onActivityResult(
                        myGetArguments().getInt(IntentExtra.REQUEST_CODE.key),
                        Activity.RESULT_CANCELED,
                        Intent()
                )
            }
        }
    }

    fun show(fragmentActivity: FragmentActivity?) {
        try {
            val ft = fragmentActivity.getSupportFragmentManager().beginTransaction()
            val prev = fragmentActivity.getSupportFragmentManager().findFragmentByTag(dialogTag)
            if (prev != null) {
                ft.remove(prev)
            }
            ft.addToBackStack(null)
            show(ft, dialogTag)
        } catch (e: Exception) {
            MyLog.w(fragmentActivity, "Failed to show " + MyStringBuilder.Companion.objToTag(this), e)
        }
    }

    fun myGetArguments(): Bundle {
        val arguments = arguments
        if (arguments != null) return arguments
        val newArguments = Bundle()
        if (!isStateSaved) setArguments(newArguments)
        return newArguments
    }

    companion object {
        val dialogTag: String? = SelectorDialog::class.java.simpleName
        fun getDialogTag(): String? {
            return dialogTag
        }
    }
}