/*
 * Copyright (C) 2017-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.actor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import org.andstatus.app.R
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.graphics.AvatarView
import org.andstatus.app.note.NoteBodyTokenizer
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.util.CollectionsUtil
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyUrlSpan

class ActorAutoCompleteAdapter(private val myActivity: LoadableListActivity<*>,
                               private val origin: Origin) : BaseAdapter(), Filterable {
    private val mInflater: LayoutInflater = LayoutInflater.from(myActivity)
    private var mFilter: ArrayFilter? = null
    private var items = FilteredValues.EMPTY

    fun getOrigin(): Origin {
        return origin
    }

    override fun getCount(): Int {
        return items.viewItems.size
    }

    override fun getItem(position: Int): ActorViewItem {
        return items.viewItems.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        view = convertView ?: mInflater.inflate(R.layout.actor_lookup, parent, false)
        val item = getItem(position)
        if (item.actor.isEmpty) {
            MyUrlSpan.showText(view, R.id.username,
                    myActivity.getText(R.string.nothing_in_the_loadable_list).toString(), false, true)
        } else {
            val username = item.actor.uniqueName
            MyUrlSpan.showAsPlainText(view, R.id.username, username, true)
            MyUrlSpan.showAsPlainText(view, R.id.description,
                    I18n.trimTextAt(item.actor.getSummary(), 80).toString(), false)
            showAvatar(view, item)
        }
        return view
    }

    private fun showAvatar(view: View, item: ActorViewItem?) {
        val avatarView: AvatarView = view.findViewById(R.id.avatar_image)
        if (item == null) {
            avatarView.visibility = View.INVISIBLE
        } else {
            item.showAvatar(myActivity, avatarView)
        }
    }

    override fun getFilter(): Filter {
        return mFilter ?: ArrayFilter().also {
            mFilter = it
        }
    }

    private class FilteredValues(val matchGroupsOnly: Boolean, val referenceChar: String, val viewItems: MutableList<ActorViewItem>) {
        companion object {
            val EMPTY: FilteredValues = FilteredValues(false, "", mutableListOf())
        }
    }

    /**
     *
     * An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.
     */
    private inner class ArrayFilter : Filter() {
        override fun performFiltering(prefixWithReferenceChar: CharSequence?): FilterResults {
            if (!origin.isValid || prefixWithReferenceChar.isNullOrEmpty() ||
                prefixWithReferenceChar.length < NoteBodyTokenizer.MIN_LENGHT_TO_SEARCH + 1) {
                val results = FilterResults()
                results.values = FilteredValues.EMPTY
                results.count = 0
                return results
            }
            val referenceChar = prefixWithReferenceChar.get(0)
            val prefixString = prefixWithReferenceChar.toString().substring(1)
            val matchGroupsOnly = origin.groupActorReferenceChar().map { c: Char? -> c == referenceChar }.orElse(false)
            val viewItems = loadFiltered(matchGroupsOnly, prefixString.toLowerCase())
            CollectionsUtil.sort(viewItems)
            val results = FilterResults()
            results.values = FilteredValues(matchGroupsOnly, referenceChar.toString(), viewItems)
            results.count = viewItems.size
            return results
        }

        private fun loadFiltered(matchGroupsOnly: Boolean, prefixString: String): MutableList<ActorViewItem> {
            val loader: ActorsLoader = object : ActorsLoader(myActivity.myContext, ActorsScreenType.ACTORS_AT_ORIGIN,
                    origin, 0, "") {
                override fun getSelection(): String {
                    return if (matchGroupsOnly) {
                        ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.id + " AND " +
                                ActorTable.TABLE_NAME + "." + ActorTable.GROUP_TYPE +
                                " IN (" + GroupType.GENERIC.id + ", " + GroupType.ACTOR_OWNED.id + ") AND " +
                                ActorTable.TABLE_NAME + "." + ActorTable.USERNAME + " LIKE '" + prefixString + "%'"
                    } else {
                        (ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.id + " AND "
                                + ActorTable.TABLE_NAME + "." + ActorTable.WEBFINGER_ID + " LIKE '" + prefixString + "%'")
                    }
                }
            }
            loader.load(null)
            val filteredValues = loader.getList()
            for (viewItem in filteredValues) {
                MyLog.v(this) { "filtered: " + viewItem.actor }
            }
            return filteredValues
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            items = results.values as FilteredValues
            if (results.count > 0) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            if (resultValue !is ActorViewItem) {
                return ""
            }
            return items.referenceChar +
                    if (resultValue.actor.isEmpty) "" else
                        if (origin.isMentionAsWebFingerId() && !items.matchGroupsOnly) resultValue.actor.uniqueName
                        else resultValue.actor.getUsername()
        }
    }

}
