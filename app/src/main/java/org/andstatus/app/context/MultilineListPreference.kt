package org.andstatus.app.context

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder

class MultilineListPreference : ListPreference {
    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}

    override fun onBindViewHolder(viewHolder: PreferenceViewHolder?) {
        super.onBindViewHolder(viewHolder)
        val textView = viewHolder.findViewById(android.R.id.title) as TextView
        if (textView != null) {
            textView.isSingleLine = false
        }
    }
}