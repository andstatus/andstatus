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
package org.andstatus.app.timeline

import android.content.Context
import android.view.ContextMenu
import android.view.View
import android.widget.TextView
import org.andstatus.app.R

/**
 * @author yvolk@yurivolkov.com
 */
class ContextMenuHeader(context: Context, contextMenu: ContextMenu) {
    private val header: View = View.inflate(context, R.layout.context_menu_header, null)

    fun setTitle(title: String?): ContextMenuHeader {
        (header.findViewById<View?>(R.id.title) as TextView).text = title
        return this
    }

    fun setSubtitle(subtitle: String?): ContextMenuHeader {
        (header.findViewById<View?>(R.id.subTitle) as TextView).text = subtitle
        return this
    }

    init {
        contextMenu.setHeaderView(header)
    }
}