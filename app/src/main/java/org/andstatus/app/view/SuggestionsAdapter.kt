/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import org.andstatus.app.R
import org.andstatus.app.SearchObjects
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.util.CollectionsUtil
import org.andstatus.app.util.MyUrlSpan
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * For now Timeline suggestions are taken from Search timelines, user suggestions are not persistent
 */
class SuggestionsAdapter(activity: LoadableListActivity<*>, searchObjects: SearchObjects) : BaseAdapter(), Filterable {
    private val mInflater: LayoutInflater?
    private val searchObjects: SearchObjects?
    private var mFilter: ArrayFilter? = null
    private var items: MutableList<String?>? = ArrayList()
    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): String? {
        return items.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View?
        view = convertView ?: mInflater.inflate(R.layout.suggestion_item, parent, false)
        val item = getItem(position)
        MyUrlSpan.Companion.showText(view, R.id.suggestion, item, false, true)
        return view
    }

    override fun getFilter(): Filter {
        if (mFilter == null) {
            mFilter = ArrayFilter()
        }
        return mFilter
    }

    /**
     *
     * An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.
     */
    private inner class ArrayFilter : Filter() {
        override fun performFiltering(prefix: CharSequence?): FilterResults? {
            var filteredValues: MutableList<String?>? = ArrayList()
            if (!TextUtils.isEmpty(prefix)) {
                val prefixString = prefix.toString().toLowerCase()
                filteredValues = loadFiltered(prefixString)
                CollectionsUtil.sort(filteredValues)
            }
            val results = FilterResults()
            results.values = filteredValues
            results.count = filteredValues.size
            return results
        }

        private fun loadFiltered(prefixString: String?): MutableList<String?>? {
            if (prefixString.isNullOrEmpty()) {
                return emptyList<String?>()
            }
            val filteredValues: MutableList<String?> = ArrayList()
            for (item in getAllSuggestions(searchObjects)) {
                if (item.toLowerCase().startsWith(prefixString)) {
                    filteredValues.add(item)
                }
            }
            if (MyPreferences.isShowDebuggingInfoInUi()) {
                filteredValues.add("prefix:$prefixString")
                filteredValues.add("inAllSuggestions:" + getAllSuggestions(searchObjects).size + "items")
            }
            return filteredValues
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            items = results.values as MutableList<String?>
            if (results.count > 0) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?): CharSequence? {
            return if (resultValue == null) {
                "(null)"
            } else resultValue as String?
        }
    }

    companion object {
        private val notesSuggestions: MutableList<String?>? = CopyOnWriteArrayList()
        private val actorsSuggestions: MutableList<String?>? = CopyOnWriteArrayList()
        fun addSuggestion(searchObjects: SearchObjects?, suggestion: String?) {
            if (suggestion.isNullOrEmpty()) {
                return
            }
            val suggestions = getAllSuggestions(searchObjects)
            if (suggestions.contains(suggestion)) {
                suggestions.remove(suggestion)
            }
            suggestions.add(0, suggestion)
        }

        private fun getAllSuggestions(searchObjects: SearchObjects?): MutableList<String?> {
            return if (SearchObjects.NOTES == searchObjects) notesSuggestions else actorsSuggestions
        }
    }

    init {
        mInflater = LayoutInflater.from(activity)
        this.searchObjects = searchObjects
        for (timeline in activity.myContext.timelines().values()) {
            if (timeline.hasSearchQuery() && !notesSuggestions.contains(timeline.searchQuery)) {
                notesSuggestions.add(timeline.searchQuery)
            }
        }
    }
}