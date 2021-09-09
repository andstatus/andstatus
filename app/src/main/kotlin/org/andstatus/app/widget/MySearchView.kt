/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.CollapsibleActionView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView.OnEditorActionListener
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.SearchObjects
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyLog
import org.andstatus.app.view.SuggestionsAdapter

/**
 * @author yvolk@yurivolkov.com
 */
class MySearchView(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs), CollapsibleActionView {
    private var parentActivity: LoadableListActivity<*>? = null
    var timeline: Timeline? = if (isInEditMode) null else Timeline.EMPTY
    var searchText: AutoCompleteTextView? = null
    var searchObjects: Spinner? = null
    var internetSearch: CheckBox? = null
    var combined: CheckBox? = null
    private var isInternetSearchEnabled = false

    fun initialize(loadableListActivity: LoadableListActivity<*>) {
        parentActivity = loadableListActivity
        searchText = findViewById<View?>(R.id.search_text) as AutoCompleteTextView
        if (searchText == null) {
            MyLog.w(this, "searchView is null")
            return
        }
        searchText?.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->

            // See https://stackoverflow.com/questions/3205339/android-how-to-make-keyboard-enter-button-say-search-and-handle-its-click
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                if (query.isNotEmpty()) {
                    onQueryTextSubmit(query)
                    return@OnEditorActionListener true
                }
            }
            false
        })
        searchObjects = findViewById<View?>(R.id.search_objects) as Spinner
        if (searchObjects == null) {
            MyLog.w(this, "searchObjects is null")
            return
        }
        searchObjects?.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSearchObjectsChange()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
        val upButton = findViewById<View?>(R.id.up_button)
        if (upButton == null) {
            MyLog.w(this, "upButton is null")
            return
        }
        upButton.setOnClickListener { onActionViewCollapsed() }
        internetSearch = findViewById<View?>(R.id.internet_search) as CheckBox
        if (internetSearch == null) {
            MyLog.w(this, "internetSearch is null")
            return
        }
        combined = findViewById(R.id.combined) as CheckBox
        if (combined == null) {
            MyLog.w(this, "combined is null")
            return
        }
        combined?.setOnCheckedChangeListener({ buttonView, isChecked -> onSearchContextChanged() })
        val submitButton = findViewById<View?>(R.id.submit_button)
        if (submitButton == null) {
            MyLog.w(this, "submitButton is null")
            return
        }
        searchText?.let { st ->
            submitButton.setOnClickListener { onQueryTextSubmit(st.getText().toString()) }
            onActionViewCollapsed()
            onSearchObjectsChange()
        }
    }

    fun showSearchView(timeline: Timeline) {
        this.timeline = timeline
        if (isCombined() != timeline.isCombined) {
            combined?.setChecked(timeline.isCombined)
        }
        onActionViewExpanded()
    }

    override fun onActionViewExpanded() {
        onSearchObjectsChange()
        visibility = VISIBLE
        parentActivity?.hideActionBar(true)
    }

    override fun onActionViewCollapsed() {
        visibility = GONE
        parentActivity?.hideActionBar(false)
        searchText?.setText("")
    }

    fun onQueryTextSubmit(query: String?) {
        MyLog.d(this, "Submitting " + getSearchObjects() + " query '" + query + "'"
                + (if (isInternetSearch()) " Internet" else "")
                + if (isCombined()) " combined" else ""
        )
        if (isInternetSearch()) {
            launchInternetSearch(query)
        }
        launchActivity(query)
        onActionViewCollapsed()
        searchText?.setAdapter(null)
        SuggestionsAdapter.addSuggestion(getSearchObjects(), query)
    }

    private fun launchInternetSearch(query: String?) {
        val myContext = parentActivity?.myContext ?: return
        if (myContext.isEmpty) return

        for (origin in myContext.origins.originsForInternetSearch(
                getSearchObjects(), getOrigin(), isCombined())) {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newSearch(getSearchObjects(), myContext, origin, query))
        }
    }

    private fun onSearchObjectsChange() {
        parentActivity?.let { activity ->
            searchText?.setAdapter(SuggestionsAdapter(activity, getSearchObjects()))
            searchText?.setHint(if (getSearchObjects() == SearchObjects.NOTES) R.string.search_timeline_hint else R.string.search_userlist_hint)
            onSearchContextChanged()
        }
    }

    private fun launchActivity(query: String?) {
        if (query.isNullOrEmpty()) {
            return
        }
        val intent = Intent(Intent.ACTION_SEARCH, getUri(), context, getSearchObjects().getActivityClass())
        intent.putExtra(IntentExtra.SEARCH_QUERY.key, query)
        if (timeline?.hasSearchQuery() == true
                && getSearchObjects() == SearchObjects.NOTES &&
                parentActivity?.myContext?.timelines?.getDefault() != timeline) {
            // Send intent to existing activity
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun onSearchContextChanged() {
        isInternetSearchEnabled = parentActivity?.myContext
                ?.origins?.isSearchSupported(
                        getSearchObjects(), getOrigin(), isCombined()) ?: false
        internetSearch?.setEnabled(isInternetSearchEnabled)
        if (!isInternetSearchEnabled && internetSearch?.isChecked() == true) {
            internetSearch?.setChecked(false)
        }
    }

    private fun getUri(): Uri? {
        return when (getSearchObjects()) {
            SearchObjects.NOTES -> timeline?.fromSearch(parentActivity?.myContext ?: return null, isInternetSearch())
                    ?.fromIsCombined(parentActivity?.myContext ?: return null, isCombined())?.getUri()
            SearchObjects.ACTORS -> MatchedUri.getActorsScreenUri(ActorsScreenType.ACTORS_AT_ORIGIN,
                    if (isCombined()) 0 else getOrigin().id, 0, "")
            SearchObjects.GROUPS -> MatchedUri.getActorsScreenUri(ActorsScreenType.GROUPS_AT_ORIGIN,
                    if (isCombined()) 0 else getOrigin().id, 0, "")
        }
    }

    fun getSearchObjects(): SearchObjects {
        return SearchObjects.fromSpinner(searchObjects)
    }

    private fun getOrigin(): Origin {
        return if (timeline?.getOrigin()?.isValid == true) timeline?.getOrigin() ?: Origin.EMPTY
        else parentActivity?.myContext?.accounts?.currentAccount?.origin ?: Origin.EMPTY
    }

    private fun isInternetSearch(): Boolean {
        return isInternetSearchEnabled && internetSearch?.isChecked() == true
    }

    private fun isCombined(): Boolean {
        return combined?.isChecked() == true
    }
}
