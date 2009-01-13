/* 
 * Copyright (C) 2008 Torgny Bjers
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

package com.xorcode.andtweet.data;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Convenience definitions for AndTweetProvider
 * 
 * @author torgny.bjers
 */
public final class AndTweet {
	public static final String AUTHORITY = "com.xorcode.andtweet";

	// This class cannot be instantiated
	private AndTweet() {
	}

	/**
	 * Tweets table
	 * 
	 * @author torgny.bjers
	 */
	public static final class Tweets implements BaseColumns {
		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/tweets");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.xorcode.andtweet.tweet";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.xorcode.andtweet.tweet";
		public static final String DEFAULT_SORT_ORDER = "sent DESC";

		// Table columns
		/**
		 * Author ID
		 * <P>
		 * Type: TEXT
		 * </P>
		 */
		public static final String AUTHOR_ID = "author_id";

		/**
		 * Tweet message
		 * <P>
		 * Type: TEXT
		 * </P>
		 */
		public static final String MESSAGE = "message";

		/**
		 * When Tweet was created
		 * <P>
		 * Type: INTEGER (long from System.currentTimeMillis()
		 * </P>
		 */
		public static final String CREATED_DATE = "created";

		/**
		 * When Tweet was originally sent
		 * <P>
		 * Type: INTEGER (long from System.currentTimeMillis()
		 * </P>
		 */
		public static final String SENT_DATE = "sent";
	}

	/**
	 * Direct Messages table
	 * 
	 * @author torgny.bjers
	 */
	public static final class DirectMessages implements BaseColumns {
		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
				+ "/directmessages");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.xorcode.andtweet.directmessage";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.xorcode.andtweet.directmessage";
		public static final String DEFAULT_SORT_ORDER = "sent DESC";

		// Table columns
		/**
		 * Author ID
		 * <P>
		 * Type: TEXT
		 * </P>
		 */
		public static final String AUTHOR_ID = "author_id";

		/**
		 * Direct Message text
		 * <P>
		 * Type: TEXT
		 * </P>
		 */
		public static final String MESSAGE = "message";

		/**
		 * When Direct Message was created
		 * <P>
		 * Type: INTEGER (long from System.currentTimeMillis()
		 * </P>
		 */
		public static final String CREATED_DATE = "created";

		/**
		 * When Direct Message was originally sent
		 * <P>
		 * Type: INTEGER (long from System.currentTimeMillis()
		 * </P>
		 */
		public static final String SENT_DATE = "sent";
	}

	/**
	 * Authors table
	 * 
	 * @author torgny.bjers
	 */
	public static final class Users implements BaseColumns {
		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/users");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.xorcode.andtweet.users";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.xorcode.andtweet.users";
		public static final String DEFAULT_SORT_ORDER = "author_id ASC";

		// Table columns
		/**
		 * Author ID
		 * <P>
		 * Type: TEXT
		 * </P>
		 */
		public static final String AUTHOR_ID = "author_id";

		/**
		 * When User was created
		 * <P>
		 * Type: INTEGER (long from System.currentTimeMillis()
		 * </P>
		 */
		public static final String MODIFIED_DATE = "modified";

		/**
		 * When User was modified
		 * <P>
		 * Type: INTEGER (long from System.currentTimeMillis()
		 * </P>
		 */
		public static final String CREATED_DATE = "created";
	}
}
