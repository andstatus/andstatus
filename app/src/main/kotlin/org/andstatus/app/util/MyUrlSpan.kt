/*
 * Copyright (C) 2015-2018 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2015 CommonsWare, LLC
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
import android.content.ActivityNotFoundException
import android.os.Parcel
import android.os.Parcelable
import android.text.Html
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

/** Prevents ActivityNotFoundException for malformed links,
 * see https://github.com/andstatus/andstatus/issues/300
 * Based on http://commonsware.com/blog/2013/10/23/linkify-autolink-need-custom-urlspan.html   */
class MyUrlSpan : URLSpan {
    class Data(val actor: Optional<Actor>, val searchQuery: Optional<String>, private val url: Optional<String>) {
        fun getURL(): String? {
            return url.orElse(getTimeline().getClickUri().toString())
        }

        fun getTimeline(): Timeline {
            return searchQuery.map({ s: String? ->
                MyContextHolder.myContextHolder.getNow().timelines()
                        .get(TimelineType.SEARCH, Actor.EMPTY, Origin.EMPTY, s)
            })
                    .orElse(actor.map({ a: Actor ->
                        MyContextHolder.myContextHolder.getNow().timelines().forUserAtHomeOrigin(TimelineType.SENT, a)
                    })
                            .orElse(Timeline.EMPTY))
        }

        override fun toString(): String = "MyUrlSpan{" +
            Stream.of(
                    actor.map { obj: Actor -> obj.toString() },
                    searchQuery.map { obj: String? -> obj.toString() },
                    url.map { obj: String? -> obj.toString() }
            )
                    .filter { obj: Optional<String> -> obj.isPresent }
                    .map { obj: Optional<String> -> obj.get() }
                    .collect(Collectors.joining(", ")) +
            '}'
    }

    val data: Data

    constructor(data: Data) : super(EMPTY_URL) {
        this.data = data
    }

    constructor(url: String) : super(url) {
        data = Data(Optional.empty(), Optional.empty(), Optional.of(url))
    }

    override fun onClick(widget: View) {
        try {
            super.onClick(widget)
        } catch (e: ActivityNotFoundException) {
            MyLog.v(this, e)
            try {
                MyLog.i(this, "Malformed link:'$url', $data")
                val myContext = MyContextHolder.myContextHolder.getNow()
                if (myContext.nonEmpty) {
                    Toast.makeText(myContext.context, myContext.context.getText(R.string.malformed_link)
                            .toString() + "\n URL:'" + url + "'", Toast.LENGTH_SHORT).show()
                }
            } catch (e2: Exception) {
                MyLog.d(this, "Couldn't show a toast", e2)
            }
        } catch (e: SecurityException) {
            MyLog.v(this, e)
            try {
                MyLog.i(this, "Malformed link:'$url', $data")
                val myContext = MyContextHolder.myContextHolder.getNow()
                if (myContext.nonEmpty) {
                    Toast.makeText(myContext.context, myContext.context.getText(R.string.malformed_link)
                            .toString() + "\n URL:'" + url + "'", Toast.LENGTH_SHORT).show()
                }
            } catch (e2: Exception) {
                MyLog.d(this, "Couldn't show a toast", e2)
            }
        }
    }

    override fun getURL(): String? {
        return data.getURL()
    }

    override fun toString(): String {
        return data.toString()
    }

    companion object {
        val EMPTY: MyUrlSpan = MyUrlSpan("")
        val SOFT_HYPHEN: String = "\u00AD"
        val EMPTY_SPANNABLE: Spannable = SpannableString("")
        val EMPTY_URL: String = "content://"

        @JvmField val CREATOR: Parcelable.Creator<MyUrlSpan> = object : Parcelable.Creator<MyUrlSpan> {
            override fun createFromParcel(parcel: Parcel): MyUrlSpan {
                return MyUrlSpan(parcel.readString() ?: "")
            }

            override fun newArray(size: Int): Array<MyUrlSpan?> {
                return arrayOfNulls<MyUrlSpan?>(size)
            }
        }

        fun showLabel(activity: Activity, @IdRes viewId: Int, @StringRes stringResId: Int) =
            showText(activity.findViewById(viewId), activity.getText(stringResId).toString(), TextMediaType.UNKNOWN, false, false)

        fun showAsPlainText(parentView: View, @IdRes viewId: Int, text: String?, showIfEmpty: Boolean) =
            showText(parentView, viewId, MyHtml.htmlToCompactPlainText(text), TextMediaType.PLAIN, false, showIfEmpty)

        fun showText(parentView: View, @IdRes viewId: Int, text: String?, linkify: Boolean, showIfEmpty: Boolean) =
            showText(parentView, viewId, text, TextMediaType.UNKNOWN, linkify, showIfEmpty)

        fun showText(parentView: View, @IdRes viewId: Int, text: String?, mediaType: TextMediaType?, linkify: Boolean, showIfEmpty: Boolean) =
            showText(parentView.findViewById(viewId), text, mediaType, linkify, showIfEmpty)

        fun showText(textView: TextView?, text: String?, mediaType: TextMediaType?, linkify: Boolean, showIfEmpty: Boolean) =
            showSpannable(textView, toSpannable(text, mediaType, linkify), showIfEmpty)

        fun showSpannable(textView: TextView?, spannable: Spannable, showIfEmpty: Boolean): TextView? {
            if (textView == null) return null
            if (spannable.length == 0) {
                textView.text = ""
                ViewUtils.showView(textView, showIfEmpty)
            } else {
                textView.text = spannable
                if (hasSpans(spannable)) {
                    textView.isFocusable = true
                    textView.isFocusableInTouchMode = true
                    textView.linksClickable = true
                    setOnTouchListener(textView)
                }
                ViewUtils.showView(textView, true)
            }
            return textView
        }

        fun toSpannable(textIn: String?, mediaType: TextMediaType?, linkify: Boolean): Spannable {
            var text = textIn
            if (text.isNullOrEmpty()) return EMPTY_SPANNABLE

            // Android 6 bug, see https://github.com/andstatus/andstatus/issues/334
            // Setting setMovementMethod to not null causes a crash if text is SOFT_HYPHEN only:
            if (text.contains(SOFT_HYPHEN)) {
                text = text.replace(SOFT_HYPHEN, "-")
            }
            val spannable = if (mediaType == TextMediaType.HTML ||
                    mediaType == TextMediaType.UNKNOWN && MyHtml.hasHtmlMarkup(text)) htmlToSpannable(text) else SpannableString(text)
            if (linkify && !hasUrlSpans(spannable)) {
                Linkify.addLinks(spannable, Linkify.WEB_URLS)
            }
            fixUrlSpans(spannable)
            return spannable
        }

        private fun htmlToSpannable(text: String?): Spannable {
            val spanned = Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
            return if (Spannable::class.java.isAssignableFrom(spanned.javaClass)) spanned as Spannable
                else SpannableString.valueOf(spanned)
        }

        fun getText(parentView: View, @IdRes viewId: Int): String {
            val view = parentView.findViewById<View?>(viewId)
            return if (view == null || !TextView::class.java.isAssignableFrom(view.javaClass)) ""
              else (view as TextView).text.toString()
        }

        /**
         * Substitute for: textView.setMovementMethod(LinkMovementMethod.getInstance());
         * setMovementMethod intercepts click on a text part without links,
         * so we replace it with our own method.
         * Solution to have clickable both links and other text is found here:
         * http://stackoverflow.com/questions/7236840/android-textview-linkify-intercepts-with-parent-view-gestures
         * following an advice from here:
         * http://stackoverflow.com/questions/7515710/listview-onclick-event-doesnt-fire-with-linkified-email-address?rq=1
         */
        fun setOnTouchListener(textView: TextView) {
            textView.setMovementMethod(null)
            textView.setOnTouchListener({ v: View, event: MotionEvent -> onTouchEvent(v, event) })
        }

        private fun onTouchEvent(view: View, event: MotionEvent): Boolean {
            val widget = view as TextView
            val text: Any? = widget.getText()
            if (text is Spanned) {
                val buffer = text
                val action = event.getAction()
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                    var x = event.getX()
                    var y = event.getY()
                    x -= widget.getTotalPaddingLeft()
                    y -= widget.getTotalPaddingTop()
                    x += widget.getScrollX()
                    y += widget.getScrollY()
                    val layout = widget.getLayout()
                    val line = layout.getLineForVertical(y.toInt())
                    val off = layout.getOffsetForHorizontal(line, x)
                    val link = buffer.getSpans(off, off,
                            ClickableSpan::class.java)
                    if (link.size > 0) {
                        if (action == MotionEvent.ACTION_UP) {
                            link[0].onClick(widget)
                        } else if (buffer is Spannable) {
                            Selection.setSelection(buffer as Spannable?,
                                    buffer.getSpanStart(link[0]),
                                    buffer.getSpanEnd(link[0]))
                        }
                        return true
                    }
                }
            }
            return false
        }

        private fun hasSpans(spanned: Spanned?): Boolean {
            if (spanned == null) return false
            val spans = spanned.getSpans(0, spanned.length, Any::class.java)
            return spans != null && spans.size > 0
        }

        private fun hasUrlSpans(spanned: Spanned?): Boolean {
            if (spanned == null) return false
            val spans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
            return spans != null && spans.size > 0
        }

        private fun fixUrlSpans(spannable: Spannable) {
            val spans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
            for (span in spans) {
                if (!MyUrlSpan::class.java.isAssignableFrom(span.javaClass)) {
                    val start = spannable.getSpanStart(span)
                    val end = spannable.getSpanEnd(span)
                    spannable.removeSpan(span)
                    spannable.setSpan(MyUrlSpan(span.url), start, end, 0)
                }
            }
        }

        fun getUrlSpans(view: View?): Array<URLSpan> {
            if (view is TextView) {
                val text = view.getText()
                if (text is Spanned) {
                    return text.getSpans(0, text.length, URLSpan::class.java)
                }
            }
            return arrayOf()
        }
    }
}
