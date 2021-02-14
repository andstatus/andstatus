package org.andstatus.app.view

import android.content.Context
import android.provider.BaseColumns
import android.view.View
import android.view.ViewGroup
import android.widget.SimpleAdapter
import org.andstatus.app.context.MyPreferences

class MySimpleAdapter(context: Context?, data: MutableList<out MutableMap<String?, *>?>?, resource: Int,
                      from: Array<String?>?, to: IntArray?, val hasActionOnClick: Boolean) : SimpleAdapter(context, data, resource, from, to), View.OnClickListener {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val view = super.getView(position, convertView, parent)
        if (!hasActionOnClick) {
            view.setOnClickListener(this)
        }
        return view
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position) as MutableMap<String?, *>
        return try {
            val id = item[BaseColumns._ID] as String?
            id?.toLong() ?: 0
        } catch (e: NumberFormatException) {
            throw NumberFormatException(e.message + " caused by wrong item: " + item)
        }
    }

    fun getPositionById(itemId: Long): Int {
        if (itemId != 0L) {
            for (position in 0 until count) {
                if (getItemId(position) == itemId) {
                    return position
                }
            }
        }
        return -1
    }

    override fun onClick(v: View?) {
        if (!MyPreferences.isLongPressToOpenContextMenu() && v.getParent() != null) {
            v.showContextMenu()
        }
    }
}