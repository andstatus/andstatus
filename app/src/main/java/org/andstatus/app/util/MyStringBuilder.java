/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import android.support.annotation.NonNull;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Adds convenience methods to {@link StringBuilder} */
public class MyStringBuilder implements CharSequence {
    public final StringBuilder builder;

    public static MyStringBuilder of(CharSequence text) {
        return new MyStringBuilder(text);
    }

    private MyStringBuilder(CharSequence text) {
        this(new StringBuilder(text));
    }

    public MyStringBuilder() {
        this(new StringBuilder());
    }

    public MyStringBuilder(StringBuilder builder) {
        this.builder = builder;
    }

    @NonNull
    public <T> MyStringBuilder withComma(CharSequence label, T obj, Predicate<T> predicate) {
        return obj == null || !predicate.test(obj)
                ? this
                : withComma(label, obj);
    }

    @NonNull
    public MyStringBuilder withComma(CharSequence label, Object obj, Supplier<Boolean> filter) {
        return obj == null || !filter.get()
                ? this
                : withComma(label, obj);
    }

    @NonNull
    public MyStringBuilder withComma(CharSequence label, Object obj) {
        return append(label, obj, ", ", false);
    }

    @NonNull
    public MyStringBuilder withCommaQuoted(CharSequence label, Object obj, boolean quoted) {
        return append(label, obj, ", ", quoted);
    }

    @NonNull
    public MyStringBuilder withComma(CharSequence text) {
        return append("", text, ", ", false);
    }

    @NonNull
    public MyStringBuilder withSpaceQuoted(CharSequence text) {
        return append("", text, " ", true);
    }

    @NonNull
    public MyStringBuilder withSpace(CharSequence text) {
        return append("", text, " ", false);
    }

    public MyStringBuilder atNewLine(CharSequence label, CharSequence text) {
        return append(label, text, ", \n", false);
    }

    public MyStringBuilder atNewLine(CharSequence text) {
        return append("", text, ", \n", false);
    }

    @NonNull
    public MyStringBuilder append(CharSequence label, Object obj, @NonNull String separator, boolean quoted) {
        if (obj == null) return this;

        String text = obj.toString();
        if (StringUtils.isEmpty(text)) return this;

        if (builder.length() > 0) builder.append(separator);
        if (StringUtils.nonEmpty(label)) builder.append(label).append(": ");
        if (quoted) builder.append("\"");
        builder.append(text);
        if (quoted) builder.append("\"");
        return this;
    }

    @NonNull
    public MyStringBuilder append(CharSequence text) {
        if (StringUtils.nonEmpty(text)) {
            builder.append(text);
        }
        return this;
    }

    @Override
    public int length() {
        return builder.length();
    }

    @Override
    public char charAt(int index) {
        return builder.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return builder.subSequence(start, end);
    }

    @Override
    @NonNull
    public String toString() {
        return builder.toString();
    }

    @NonNull
    public static StringBuilder appendWithSpace(StringBuilder builder, CharSequence text) {
        return new MyStringBuilder(builder).withSpace(text).builder;
    }

    @NonNull
    public MyStringBuilder prependWithSeparator(CharSequence text, @NonNull String separator) {
        if (text.length() > 0) {
            builder.insert(0, separator);
            builder.insert(0, text);
        }
        return this;
    }

}
