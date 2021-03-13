/*
 * Copyright (C) 2011 yvolk (Yuri Volkov), http://yurivolkov.com
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

/**
 * Add selection and it's argument (for query...)
 */
class SelectionAndArgs @JvmOverloads constructor(selection_in: String = "") : TaggedClass {
    @Volatile
    var selection: String

    @Volatile
    var selectionArgs: Array<String> = arrayOf()

    @Volatile
    private var nArgs = 0
    fun clear() {
        selection = ""
        selectionArgs = arrayOf()
        nArgs = 0
    }

    fun addSelection(selection_in: String?): Int {
        return addSelection(selection_in, emptyArray())
    }

    fun addSelection(selectionAdd: String?, selectionArgAdd: String?): Int {
        return addSelection(selectionAdd, selectionArgAdd?.let { arrayOf(it) } ?: emptyArray())
    }

    fun addSelection(selectionAdd: String?, selectionArgsAdd: Array<String>?): Int {
        val nArgsAdd = selectionArgsAdd?.size ?: 0
        if (!selectionAdd.isNullOrEmpty()) {
            selection = if (selection.isEmpty()) {
                selectionAdd
            } else {
                "($selection) AND ($selectionAdd)"
            }
        }
        if (nArgsAdd > 0 && selectionArgsAdd != null) {
            val nArgs2 = nArgs + nArgsAdd
            selectionArgs += selectionArgsAdd
            nArgs = nArgs2
        }
        return nArgs
    }

    fun isEmpty(): Boolean {
        return nArgs == 0
    }

    override fun toString(): String {
        return MyStringBuilder.formatKeyValue(this, selection + ", args:" + selectionArgs.contentToString())
    }

    override fun classTag(): String {
        return TAG
    }

    companion object {
        private val TAG: String = SelectionAndArgs::class.java.simpleName
    }

    init {
        clear()
        selection = selection_in
    }
}