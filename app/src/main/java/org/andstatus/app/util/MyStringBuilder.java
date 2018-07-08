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
import android.text.TextUtils;

/** Adds convenience methods to {@link StringBuilder} */
public class MyStringBuilder implements CharSequence {
    public final StringBuilder builder;

    public MyStringBuilder(CharSequence text) {
        this(new StringBuilder(text));
    }

    public MyStringBuilder() {
        this(new StringBuilder());
    }

    public MyStringBuilder(StringBuilder builder) {
        this.builder = builder;
    }

    @NonNull
    public MyStringBuilder withComma(CharSequence text) {
        return withSeparator(text, ", ");
    }

    @NonNull
    public MyStringBuilder withSpaceQuoted(CharSequence text) {
        return withSpace("\"").append(text).append("\"");
    }

    @NonNull
    public MyStringBuilder withSpace(CharSequence text) {
        return withSeparator(text, " ");
    }

    public MyStringBuilder atNewLine(CharSequence text) {
        return withSeparator(text, ", \n");
    }

    @NonNull
    public MyStringBuilder withSeparator(CharSequence text, @NonNull String separator) {
        if (!TextUtils.isEmpty(text)) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(text);
        }
        return this;
    }

    @NonNull
    public MyStringBuilder append(CharSequence text) {
        builder.append(text);
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
    public static StringBuilder appendWithComma(StringBuilder builder, CharSequence text) {
        return new MyStringBuilder(builder).withComma(text).builder;
    }

    @NonNull
    public static StringBuilder appendWithSpace(StringBuilder builder, CharSequence text) {
        return new MyStringBuilder(builder).withSpace(text).builder;
    }

    @NonNull
    public static StringBuilder appendWithSeparator(StringBuilder builder, CharSequence text, @NonNull String separator) {
        return new MyStringBuilder(builder).withSeparator(text, separator).builder;
    }

    public static StringBuilder appendAtNewLine(StringBuilder builder, CharSequence text) {
        return new MyStringBuilder(builder).atNewLine(text).builder;
    }
}
