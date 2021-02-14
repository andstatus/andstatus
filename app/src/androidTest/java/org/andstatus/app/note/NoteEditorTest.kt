/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.noteimport

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ReplaceTextAction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.HelpActivity
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyContentType
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Audience
import org.andstatus.app.note.BaseNoteViewItem
import org.andstatus.app.note.ConversationActivity
import org.andstatus.app.note.NoteContextMenuItem
import org.andstatus.app.note.NoteEditorData
import org.andstatus.app.note.NoteEditorTest
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.ListScreenTestHelper
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyHtmlTest
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.ScreenshotOnFailure
import org.andstatus.app.util.StringUtil
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

eu.bolt.screenshotty.ScreenshotManagerBuilder.build
import eu.bolt.screenshotty.ScreenshotManager.makeScreenshot
import eu.bolt.screenshotty.ScreenshotResult.observe
import eu.bolt.screenshotty.util.ScreenshotFileSaver.Companion.create
import eu.bolt.screenshotty.util.ScreenshotFileSaver.saveToFile
import org.andstatus.app.util.StringUtil
import org.andstatus.app.os.MyAsyncTask.PoolEnum
import android.os.AsyncTask
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.InstanceId
import kotlin.jvm.Volatile
import org.andstatus.app.os.ExceptionsCounter
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteDatabaseLockedException
import org.andstatus.app.util.RelativeTime
import android.os.Looper
import io.vavr.control.Try
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.os.UiThreadExecutor
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.util.TryUtils
import org.andstatus.app.context.MyContextHolder
import org.acra.ACRA
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.R
import org.andstatus.app.os.NonUiThreadExecutor
import org.andstatus.app.net.http.HttpConnectionOAuth
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.extractors.TokenExtractor
import com.github.scribejava.core.model.OAuth2AccessToken
import org.andstatus.app.net.http.MyOAuth2AccessTokenJsonExtractor
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.http.HttpConnectionData
import com.github.scribejava.core.model.Verb
import org.andstatus.app.service.ConnectionRequired
import org.json.JSONObject
import org.andstatus.app.util.UriUtils
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.SslModeEnum
import oauth.signpost.OAuthConsumer
import kotlin.Throws
import org.andstatus.app.net.http.ConnectionException
import oauth.signpost.OAuthProvider
import com.github.scribejava.core.oauth.OAuth20Service
import org.andstatus.app.net.http.HttpConnectionInterface
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.util.UrlUtils
import org.json.JSONArray
import io.vavr.control.CheckedFunction
import org.json.JSONException
import org.andstatus.app.util.JsonUtils
import org.json.JSONTokener
import org.andstatus.app.net.http.HttpConnectionUtils
import org.andstatus.app.net.http.OAuthClientKeysStrategy
import org.andstatus.app.net.http.OAuthClientKeys
import org.andstatus.app.net.http.OAuthClientKeysOpenSource
import org.andstatus.app.net.http.OAuthClientKeysDynamic
import org.andstatus.app.account.AccountName
import org.andstatus.app.account.AccountDataReader
import org.andstatus.app.util.TriState
import org.andstatus.app.data.MyContentType
import org.andstatus.app.account.AccountConnectionData
import kotlin.jvm.JvmOverloads
import android.content.res.Resources.NotFoundException
import org.andstatus.app.net.http.HttpConnectionApacheSpecific
import org.andstatus.app.net.http.HttpConnectionApacheCommon
import cz.msebera.android.httpclient.client.methods.HttpPost
import org.andstatus.app.net.http.ApacheHttpClientUtils
import cz.msebera.android.httpclient.HttpEntity
import cz.msebera.android.httpclient.client.methods.HttpGet
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.net.http.HttpConnectionEmpty
import org.andstatus.app.net.http.HttpConnectionUtils.ReadChecker
import org.andstatus.app.service.ConnectionState
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory
import cz.msebera.android.httpclient.config.RegistryBuilder
import cz.msebera.android.httpclient.conn.socket.PlainConnectionSocketFactory
import org.andstatus.app.net.http.TlsSniSocketFactory
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager
import cz.msebera.android.httpclient.client.config.RequestConfig
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder
import cz.msebera.android.httpclient.impl.client.HttpClients
import cz.msebera.android.httpclient.conn.socket.LayeredConnectionSocketFactory
import android.net.SSLCertificateSocketFactory
import cz.msebera.android.httpclient.HttpHost
import org.andstatus.app.net.http.MultipartFormEntityBytes
import cz.msebera.android.httpclient.protocol.HTTP
import android.content.ContentResolver
import cz.msebera.android.httpclient.message.BasicNameValuePair
import org.andstatus.app.net.http.MisconfiguredSslHttpClientFactory
import org.andstatus.app.net.http.MyHttpClientFactory
import org.andstatus.app.net.http.OAuthClientKeysSecret
import org.andstatus.app.util.SharedPreferencesUtil
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer
import cz.msebera.android.httpclient.impl.client.BasicResponseHandler
import oauth.signpost.exception.OAuthMessageSignerException
import oauth.signpost.exception.OAuthExpectationFailedException
import oauth.signpost.exception.OAuthCommunicationException
import cz.msebera.android.httpclient.HttpVersion
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity
import cz.msebera.android.httpclient.StatusLine
import oauth.signpost.basic.DefaultOAuthProvider
import org.andstatus.app.net.http.HttpConnectionOAuthJavaNet
import oauth.signpost.basic.DefaultOAuthConsumer
import org.apache.commons.lang3.tuple.ImmutablePair
import org.andstatus.app.net.http.HttpConnectionOAuth2JavaNet
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig
import com.github.scribejava.core.builder.ServiceBuilder
import org.andstatus.app.net.http.OAuthApi20
import com.github.scribejava.core.model.OAuthConstants
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor
import cz.msebera.android.httpclient.conn.scheme.SchemeRegistry
import cz.msebera.android.httpclient.conn.scheme.Scheme
import cz.msebera.android.httpclient.conn.scheme.PlainSocketFactory
import cz.msebera.android.httpclient.conn.ClientConnectionManager
import cz.msebera.android.httpclient.impl.conn.tsccm.ThreadSafeClientConnManager
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient
import cz.msebera.android.httpclient.params.BasicHttpParams
import cz.msebera.android.httpclient.params.HttpProtocolParams
import org.andstatus.app.net.social.pumpio.PObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.pumpio.PActivityType
import org.andstatus.app.net.social.pumpio.ConnectionPumpio
import org.andstatus.app.net.social.AActivity
import io.vavr.control.CheckedPredicate
import org.andstatus.app.net.social.Actor
import org.andstatus.app.actor.GroupType
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.origin.OriginPumpio
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.net.social.InputTimelinePage
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.Audience
import org.andstatus.app.util.ObjectOrId
import org.andstatus.app.net.social.AObject
import org.andstatus.app.util.LazyVal
import org.andstatus.app.net.social.Attachments
import org.andstatus.app.util.MyHtml
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import android.provider.BaseColumns
import org.andstatus.app.data.DbUtils
import org.andstatus.app.account.MyAccount
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.net.social.ActorEndpoints
import org.andstatus.app.data.AvatarFile
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.util.NullUtil
import org.andstatus.app.origin.ActorReference
import org.andstatus.app.context.ActorInTimeline
import org.andstatus.app.data.ActorSql
import org.andstatus.app.net.social.activitypub.ApObjectType
import org.andstatus.app.net.social.activitypub.ConnectionActivityPub
import org.andstatus.app.net.social.ConnectionMastodon
import org.andstatus.app.net.social.InputActorPage
import org.andstatus.app.net.social.AJsonCollection
import io.vavr.control.CheckedConsumer
import org.andstatus.app.util.TaggedClass
import org.andstatus.app.util.CollectionsUtil
import org.andstatus.app.data.MyProvider
import org.andstatus.app.database.table.AudienceTable
import org.andstatus.app.data.SqlIds
import android.content.ContentValues
import android.text.SpannableString
import android.text.Spanned
import org.andstatus.app.net.social.SpanUtil
import android.text.Spannable
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.util.HasEmpty
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadType
import org.andstatus.app.net.social.RateLimitStatus
import org.andstatus.app.net.social.ConnectionEmpty
import org.andstatus.app.net.social.ApiDebugger
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.service.FileDownloader
import org.andstatus.app.note.NoteDownloads
import org.andstatus.app.database.table.ActorEndpointTable
import org.andstatus.app.net.social.InputPage
import org.andstatus.app.net.social.ConnectionTwitterLike
import org.andstatus.app.note.KeywordsFilter
import org.andstatus.app.net.social.ConnectionTheTwitter
import android.os.Build
import org.andstatus.app.net.social.ConnectionTwitterGnuSocial
import org.andstatus.app.data.checker.DataChecker
import org.andstatus.app.data.checker.CheckUsers.CheckResults
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.data.checker.CheckTimelines
import org.andstatus.app.data.checker.CheckDownloads
import org.andstatus.app.data.checker.MergeActors
import org.andstatus.app.data.checker.CheckUsers
import org.andstatus.app.data.checker.CheckConversations
import org.andstatus.app.data.checker.CheckAudience
import org.andstatus.app.data.checker.SearchIndexUpdate
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.checker.CheckAudience.FixSummary
import org.andstatus.app.data.checker.CheckDownloads.Results
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.database.table.TimelineTable
import org.andstatus.app.timeline.meta.TimelineSaver
import org.andstatus.app.data.checker.CheckConversations.NoteItem
import org.andstatus.app.data.converter.ConvertOneStep
import org.andstatus.app.data.converter.Convert15
import android.database.sqlite.SQLiteDatabase
import android.accounts.AccountManager
import org.andstatus.app.account.AccountUtils
import org.andstatus.app.data.converter.DatabaseConverterController
import org.andstatus.app.data.converter.AndroidAccountData
import org.andstatus.app.account.AccountData
import org.andstatus.app.account.CredentialsVerificationStatus
import org.andstatus.app.data.converter.AccountConverter
import org.andstatus.app.context.MyStorage
import org.andstatus.app.account.MyAccounts
import org.andstatus.app.data.converter.Convert47
import org.andstatus.app.data.converter.DatabaseConverterController.UpgradeParams
import org.andstatus.app.data.converter.ApplicationUpgradeException
import android.app.Activity
import org.andstatus.app.MyActivity
import org.andstatus.app.backup.DefaultProgressListener
import org.andstatus.app.data.converter.DatabaseConverter
import org.andstatus.app.data.converter.DatabaseConverterController.AsyncUpgrade
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteStatement
import android.database.sqlite.SQLiteDoneException
import org.andstatus.app.data.TimelineSql
import org.andstatus.app.data.ActorToNote
import org.andstatus.app.database.table.UserTable
import org.andstatus.app.data.SqlWhere
import org.andstatus.app.data.DownloadFile
import org.andstatus.app.graphics.IdentifiableImageView
import org.andstatus.app.graphics.AttachedImageView
import org.andstatus.app.graphics.CachedImage
import org.andstatus.app.data.MediaFile.ImageLoader
import org.andstatus.app.graphics.CacheName
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.data.MediaFile
import org.andstatus.app.data.MediaFile.AbstractImageLoader
import android.graphics.drawable.Drawable
import org.andstatus.app.data.MediaFile.DrawableLoader
import android.content.Intent
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.data.ParsedUri
import org.andstatus.app.IntentExtra
import org.andstatus.app.data.AvatarData
import org.andstatus.app.data.DataPruner
import android.content.SharedPreferences
import org.andstatus.app.util.SelectionAndArgs
import org.andstatus.app.data.DownloadData.ConsumedSummary
import org.andstatus.app.timeline.meta.DisplayedInSelector
import org.andstatus.app.ClassInApplicationPackage
import android.content.UriMatcher
import android.content.ContentUris
import org.andstatus.app.database.table.OriginTable
import android.content.ContentProvider
import android.database.sqlite.SQLiteQueryBuilder
import org.andstatus.app.data.ProjectionMap
import org.andstatus.app.database.table.GroupMembersTable
import org.andstatus.app.data.LatestActorActivities
import org.andstatus.app.data.ActorActivity
import android.webkit.MimeTypeMap
import org.andstatus.app.data.ContentValuesUtils
import android.os.ParcelFileDescriptor
import org.andstatus.app.data.DownloadStatus.CanBeDownloaded
import android.database.sqlite.SQLiteConstraintException
import org.andstatus.app.context.DemoData
import org.andstatus.app.note.NoteEditorData
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.AttachedMediaFile
import org.andstatus.app.data.NoteForAnyAccount
import org.andstatus.app.data.AttachedImageFiles
import org.andstatus.app.data.NoteContextMenuData
import org.andstatus.app.data.DemoConversationInserter
import org.hamcrest.MatcherAssert
import org.hamcrest.CoreMatchers
import org.andstatus.app.data.DemoGnuSocialConversationInserter
import org.andstatus.app.lang.SelectableEnum
import android.widget.ArrayAdapter
import org.andstatus.app.lang.SelectableEnumList
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher
import org.andstatus.app.list.MyBaseListActivity
import android.os.Bundle
import org.andstatus.app.widget.MySwipeRefreshLayout.CanSwipeRefreshScrollUpCallback
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import org.andstatus.app.widget.MySwipeRefreshLayout
import org.andstatus.app.timeline.EmptyBaseTimelineAdapter
import org.andstatus.app.note.NoteEditor
import org.andstatus.app.note.NoteEditorCommand
import org.andstatus.app.service.MyServiceEventsBroadcaster
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.service.MyServiceEvent
import org.andstatus.app.note.NoteShare
import org.andstatus.app.note.NoteEditorContainer
import android.view.ViewGroup
import org.andstatus.app.note.NoteBodyTokenizer
import org.andstatus.app.note.NoteEditor.ScreenToggleState
import org.andstatus.app.note.NoteEditorBodyView
import android.widget.TextView
import android.view.LayoutInflater
import android.widget.RelativeLayout
import android.text.TextWatcher
import android.text.Editable
import android.widget.TextView.OnEditorActionListener
import android.view.View.OnTouchListener
import android.view.MotionEvent
import org.andstatus.app.note.SharedNote
import org.andstatus.app.util.MyCheckBox
import org.andstatus.app.actor.ActorAutoCompleteAdapter
import android.widget.LinearLayout
import android.widget.Toast
import org.andstatus.app.note.NoteSaver
import org.andstatus.app.note.NoteEditorLock
import org.andstatus.app.ActivityRequestCode
import android.provider.MediaStore
import org.andstatus.app.timeline.LoadableListActivity
import android.os.Parcelable
import org.andstatus.app.note.NoteContextMenu
import org.andstatus.app.timeline.TimelineData
import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.note.BaseNoteAdapter
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.note.BaseNoteViewItem
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.note.KeywordsFilter.Keyword
import org.andstatus.app.note.ConversationLoader
import org.andstatus.app.note.ConversationLoaderFactory
import org.andstatus.app.note.ConversationViewItem
import org.andstatus.app.actor.MentionedActorsLoader
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.note.ConversationIndentImageView
import org.andstatus.app.note.NoteContextMenuItem
import org.andstatus.app.note.NoteContextMenuContainer
import org.andstatus.app.view.MyContextMenu
import org.andstatus.app.note.FutureNoteContextMenuData
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import org.andstatus.app.note.FutureNoteContextMenuData.StateForSelectedViewItem
import org.andstatus.app.timeline.ContextMenuHeader
import android.view.accessibility.AccessibilityManager
import org.andstatus.app.note.ConversationActivity
import org.andstatus.app.activity.ActivityViewItem
import android.text.style.URLSpan
import android.view.SubMenu
import org.andstatus.app.timeline.ViewItem
import android.text.Html
import org.andstatus.app.timeline.DuplicationLink
import org.andstatus.app.timeline.TimelineFilter
import org.andstatus.app.actor.ActorsLoader
import android.widget.MultiAutoCompleteTextView.Tokenizer
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.note.ConversationLoader.ReplyLevelComparator
import org.andstatus.app.note.ConversationLoader.OrderCounters
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import org.andstatus.app.timeline.TimelinePage
import org.andstatus.app.timeline.TimelineParameters
import org.andstatus.app.timeline.WhichPage
import org.andstatus.app.graphics.AvatarView
import org.andstatus.app.list.ContextMenuItem
import org.andstatus.app.account.AccountSelector
import org.andstatus.app.MyAction
import org.andstatus.app.timeline.LoadableListViewParameters
import android.content.ClipData
import org.andstatus.app.note.NoteEditorListActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import android.widget.CompoundButton
import org.andstatus.app.service.QueueViewer
import org.andstatus.app.timeline.LoadableListPosition
import android.widget.CheckBox
import org.andstatus.app.util.BundleUtils
import org.andstatus.app.note.ConversationAdapter
import org.andstatus.app.timeline.ListScope
import org.andstatus.app.note.RecursiveConversationLoader
import org.andstatus.app.note.PrivateNotesConversationLoader
import androidx.appcompat.widget.AppCompatImageView
import android.view.View.MeasureSpec
import android.widget.ImageView.ScaleType
import org.andstatus.app.user.CachedUsersAndActors
import org.andstatus.app.util.Xslt
import org.apache.commons.lang3.text.translate.CharSequenceTranslator
import org.apache.commons.lang3.text.translate.AggregateTranslator
import org.apache.commons.lang3.text.translate.LookupTranslator
import org.apache.commons.lang3.text.translate.EntityArrays
import org.apache.commons.lang3.text.translate.NumericEntityUnescaper
import kotlin.jvm.Synchronized
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.content.ActivityNotFoundException
import android.os.Parcel
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import android.text.util.Linkify
import android.text.style.ClickableSpan
import android.text.Selection
import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.ColorInt
import androidx.annotation.AttrRes
import android.content.res.Resources.Theme
import android.util.TypedValue
import org.andstatus.app.util.Permissions.PermissionType
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.DialogInterface
import android.view.WindowManager
import org.andstatus.app.util.DialogFactory.OkCancelDialogFragment
import android.widget.EditText
import org.andstatus.app.util.TypedCursorValue.CursorFieldType
import org.andstatus.app.util.TypedCursorValue
import androidx.documentfile.provider.DocumentFile
import org.andstatus.app.util.DocumentFileUtils
import org.andstatus.app.util.TamperingDetector
import android.content.pm.PackageInfo
import org.andstatus.app.util.FileDescriptorUtils
import androidx.preference.PreferenceFragmentCompat
import android.content.SharedPreferences.Editor
import org.andstatus.app.view.SelectorDialog
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView
import org.andstatus.app.view.MySimpleAdapter
import org.andstatus.app.view.EnumSelector
import android.view.View.OnCreateContextMenuListener
import org.andstatus.app.timeline.EmptyViewItem
import org.andstatus.app.context.MyTheme
import androidx.fragment.app.FragmentActivity
import android.widget.SimpleAdapter
import org.andstatus.app.SearchObjects
import android.widget.BaseAdapter
import android.widget.Filter.FilterResults
import org.andstatus.app.actor.GroupType.IsGroupLike
import org.andstatus.app.actor.GroupType.IsCollection
import org.andstatus.app.actor.GroupType.IsActorOwned
import org.andstatus.app.actor.ActorContextMenu
import org.andstatus.app.actor.ActorViewItemPopulator
import org.andstatus.app.actor.ActorsOfNoteLoader
import org.andstatus.app.actor.ActorAdapter
import org.andstatus.app.actor.ActorsScreen
import org.andstatus.app.actor.FriendsAndFollowersLoader
import org.andstatus.app.actor.ActorContextMenuItem
import org.andstatus.app.actor.ActorAutoCompleteAdapter.FilteredValues
import android.app.backup.BackupAgent
import org.andstatus.app.backup.MyBackupDescriptor
import android.app.backup.BackupDataOutput
import org.andstatus.app.backup.MyBackupDataOutput
import org.andstatus.app.backup.MyBackupAgent
import org.andstatus.app.database.DatabaseHolder
import android.app.backup.BackupDataInput
import org.andstatus.app.backup.MyBackupDataInput
import org.andstatus.app.context.MyContextState
import org.andstatus.app.FirstActivity
import org.andstatus.app.backup.BackupActivity.BackupTask
import android.provider.DocumentsContract
import org.andstatus.app.backup.MyBackupManager
import org.andstatus.app.backup.BackupActivity
import org.andstatus.app.backup.ProgressLogger.EmptyListener
import org.andstatus.app.backup.RestoreActivity.RestoreTask
import org.andstatus.app.backup.RestoreActivity
import org.andstatus.app.backup.MyBackupDataInput.BackupHeader
import android.app.ProgressDialog
import org.andstatus.app.list.MyListActivity
import org.andstatus.app.origin.OriginList
import org.andstatus.app.origin.OriginEditor
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.origin.OriginType.ApiEnum
import org.andstatus.app.origin.OriginType.NoteName
import org.andstatus.app.origin.OriginType.NoteSummary
import org.andstatus.app.origin.OriginType.PublicChangeAllowed
import org.andstatus.app.origin.OriginType.FollowersChangeAllowed
import org.andstatus.app.origin.OriginType.SensitiveChangeAllowed
import org.andstatus.app.origin.OriginType.ShortUrlLength
import org.andstatus.app.origin.OriginTwitter
import org.andstatus.app.net.http.HttpConnectionOAuthApache
import org.andstatus.app.net.http.HttpConnectionBasic
import org.andstatus.app.origin.OriginActivityPub
import org.andstatus.app.net.http.HttpConnectionOAuthMastodon
import org.andstatus.app.origin.OriginGnuSocial
import org.andstatus.app.origin.OriginMastodon
import android.widget.Spinner
import org.andstatus.app.origin.OriginEditor.AddOrigin
import org.andstatus.app.origin.DiscoveredOrigins
import org.andstatus.app.origin.OriginEditor.SaveOrigin
import android.widget.AdapterView.OnItemSelectedListener
import android.view.View.OnFocusChangeListener
import android.media.RingtoneManager
import android.media.Ringtone
import org.andstatus.app.origin.OriginSelector
import org.andstatus.app.context.MyContextImpl
import org.andstatus.app.origin.PersistentOrigins
import org.andstatus.app.service.MyServiceEventsListener
import org.andstatus.app.service.MyServiceEventsReceiver
import org.andstatus.app.origin.DiscoveredOriginList
import android.widget.AutoCompleteTextView
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import org.andstatus.app.context.MyLocale
import androidx.annotation.RawRes
import androidx.appcompat.widget.AppCompatEditText
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.andstatus.app.account.AuthenticatorService
import android.content.PeriodicSync
import com.woxthebox.draglistview.DragListView
import androidx.recyclerview.widget.LinearLayoutManager
import org.andstatus.app.account.AccountListFragment.ItemAdapter
import org.andstatus.app.account.AccountListFragment.MyDragItem
import com.woxthebox.draglistview.DragItem
import org.andstatus.app.util.MyResources
import com.woxthebox.draglistview.DragItemAdapter
import org.andstatus.app.account.AccountListFragment.ItemAdapter.AccountViewHolder
import org.andstatus.app.account.AccountSettingsActivity
import org.andstatus.app.account.DemoAccountInserter
import android.accounts.AbstractAccountAuthenticator
import android.accounts.NetworkErrorException
import android.accounts.AccountAuthenticatorResponse
import android.os.IBinder
import org.andstatus.app.account.AccountListFragment
import org.andstatus.app.account.AccountSettingsActivity.FragmentAction
import org.andstatus.app.account.AccountSettingsActivity.ResultStatus
import org.andstatus.app.account.AccountSettingsActivity.ActivityOnFinish
import org.andstatus.app.account.StateOfAccountChangeProcess
import org.andstatus.app.account.InstanceForNewAccountFragment
import org.andstatus.app.account.AccountSettingsFragment
import org.andstatus.app.origin.PersistentOriginList
import org.andstatus.app.account.AccountSettingsActivity.OAuthAcquireAccessTokenTask
import org.andstatus.app.account.AccountSettingsActivity.VerifyCredentialsTask
import org.andstatus.app.account.AccountSettingsActivity.OAuthRegisterClientTask
import org.andstatus.app.account.AccountSettingsActivity.OAuthAcquireRequestTokenTask
import oauth.signpost.exception.OAuthNotAuthorizedException
import org.andstatus.app.account.AccountSettingsWebActivity
import oauth.signpost.OAuth
import io.vavr.control.NonFatalException
import org.andstatus.app.account.AccountSettingsWebActivity.WebViewListener
import android.webkit.WebViewClient
import android.graphics.Bitmap
import org.andstatus.app.origin.DemoOriginInserter
import org.andstatus.app.context.DemoData.MyAsyncTaskDemoData
import android.os.LocaleList
import android.content.ContextWrapper
import org.andstatus.app.timeline.meta.PersistentTimelines
import org.andstatus.app.service.CommandQueue
import org.andstatus.app.notification.Notifier
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.context.ExecutionMode
import org.acra.annotation.AcraMailSender
import org.acra.annotation.AcraDialog
import org.acra.annotation.AcraCore
import android.app.Application
import org.andstatus.app.context.MyApplication
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.DatabaseErrorHandler
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import org.andstatus.app.timeline.TapOnATimelineTitleBehaviour
import android.app.backup.BackupManager
import org.andstatus.app.context.MySettingsFragment
import org.andstatus.app.context.StorageSwitch.MoveDataBetweenStoragesTask
import org.andstatus.app.context.StorageSwitch
import org.andstatus.app.context.MyFutureContext
import org.andstatus.app.syncadapter.SyncInitiator
import android.util.AndroidRuntimeException
import org.andstatus.app.HelpActivity
import org.andstatus.app.context.MySettingsGroup
import org.andstatus.app.MyActivity.OnFinishAction
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import org.andstatus.app.notification.NotificationMethodType
import org.andstatus.app.account.ManageAccountsActivity
import org.andstatus.app.timeline.meta.ManageTimelines
import androidx.preference.PreferenceViewHolder
import org.andstatus.app.service.QueueExecutors
import org.andstatus.app.service.MyService.HeartBeat
import android.os.PowerManager.WakeLock
import org.andstatus.app.service.MyService
import android.content.BroadcastReceiver
import android.content.IntentFilter
import org.andstatus.app.appwidget.AppWidgets
import android.os.PowerManager
import org.andstatus.app.service.QueueExecutor
import org.andstatus.app.service.QueueType
import org.andstatus.app.service.QueueData
import org.andstatus.app.service.CommandTimeline
import org.andstatus.app.service.CommandResult
import org.andstatus.app.database.table.CommandTable
import org.andstatus.app.service.CommandQueue.OneQueue
import org.andstatus.app.service.QueueViewerAdapter
import android.view.MenuInflater
import org.andstatus.app.service.CommandQueue.AccessorType
import org.andstatus.app.service.CommandExecutorParent
import org.andstatus.app.service.CommandExecutorStrategy
import org.andstatus.app.service.AvatarDownloader
import org.andstatus.app.service.AttachmentDownloader
import org.andstatus.app.service.MyServiceManager.MyServiceStateInTime
import org.andstatus.app.service.MyServiceManager.ServiceAvailability
import org.andstatus.app.service.TimelineSyncTracker
import org.andstatus.app.service.CommandExecutorOther
import org.andstatus.app.service.CommandExecutorGetOpenInstances
import org.andstatus.app.service.TimelineDownloaderFollowers
import org.andstatus.app.service.TimelineDownloaderOther
import org.andstatus.app.service.CommandExecutorFollowers
import android.content.SyncResult
import org.andstatus.app.service.TimelineDownloader
import org.andstatus.app.activity.ActivityContextMenu
import org.andstatus.app.note.NoteAdapter
import org.andstatus.app.activity.ActivityAdapter.LayoutType
import org.andstatus.app.activity.TimelineDataActorWrapper
import org.andstatus.app.activity.TimelineDataNoteWrapper
import org.andstatus.app.activity.TimelineDataObjActorWrapper
import org.andstatus.app.activity.ActorOfActivityContextMenu
import org.andstatus.app.activity.TimelineDataWrapper
import android.database.sqlite.SQLiteOpenHelper
import org.andstatus.app.database.DatabaseCreator
import android.util.DisplayMetrics
import org.andstatus.app.graphics.ImageCacheApi28Helper
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import org.andstatus.app.graphics.BitmapSubsetDrawable
import android.view.Display
import org.apache.commons.lang3.time.DurationFormatUtils
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.annotation.TargetApi
import android.graphics.ImageDecoder.OnHeaderDecodedListener
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Animatable
import org.andstatus.app.timeline.meta.TimelineTitle
import org.andstatus.app.timeline.meta.ManageTimelinesContextMenu
import org.andstatus.app.timeline.meta.ManageTimelinesViewItem
import org.andstatus.app.timeline.meta.ManageTimelinesViewItemComparator
import org.andstatus.app.timeline.meta.TimelineSelector
import org.andstatus.app.timeline.meta.ManageTimelinesContextMenuItem
import org.andstatus.app.timeline.ViewItemType
import org.andstatus.app.timeline.DuplicatesCollapser
import android.widget.AbsListView
import org.andstatus.app.test.SelectorActivityMock
import org.andstatus.app.actor.ActorProfileViewer
import org.andstatus.app.widget.MySearchView
import org.andstatus.app.activity.ActivityAdapter
import org.andstatus.app.timeline.TimelineViewPositionStorage
import android.view.Gravity
import org.andstatus.app.timeline.TimelineLoader
import org.andstatus.app.timeline.DuplicatesCollapser.ItemWithPage
import org.andstatus.app.timeline.DuplicatesCollapser.GroupToCollapse
import org.andstatus.app.timeline.LoadableListActivity.AsyncLoader
import org.andstatus.app.notification.NotificationEvents
import org.andstatus.app.appwidget.MyAppWidgetData
import android.appwidget.AppWidgetManager
import org.andstatus.app.appwidget.MyRemoteViewData
import android.widget.RemoteViews
import android.content.ComponentName
import org.andstatus.app.appwidget.MyAppWidgetProvider
import android.app.PendingIntent
import android.appwidget.AppWidgetProvider
import org.andstatus.app.appwidget.MyAppWidgetConfigure
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import org.andstatus.app.service.MyServiceCommandsRunner
import org.andstatus.app.syncadapter.SyncService.ResourceHolder
import org.andstatus.app.syncadapter.SyncAdapter
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.NotificationChannel
import androidx.appcompat.app.AppCompatActivity
import android.view.InflateException
import androidx.viewpager.widget.ViewPager
import androidx.fragment.app.FragmentPagerAdapter
import org.andstatus.app.HelpActivity.LogoFragment
import androidx.viewpager.widget.PagerAdapter
import org.andstatus.app.FirstActivity.NeedToStart
import cz.msebera.android.httpclient.client.methods.HttpUriRequest
import cz.msebera.android.httpclient.HttpEntityEnclosingRequest
import oauth.signpost.AbstractOAuthConsumer
import oauth.signpost.commonshttp.HttpRequestAdapter
import oauth.signpost.AbstractOAuthProvider
import oauth.signpost.commonshttp.HttpResponseAdapter
import org.junit.rules.TestRule
import org.apache.geode.test.junit.ConditionalIgnore
import org.apache.geode.test.junit.rules.ConditionalIgnoreRule
import org.apache.geode.test.junit.IgnoreCondition
import org.apache.geode.test.junit.support.IgnoreConditionEvaluationException
import org.apache.geode.test.junit.support.DefaultIgnoreCondition
import kotlin.reflect.KClass
import org.andstatus.app.util.RawResourceUtils
import org.junit.Before
import org.andstatus.app.net.social.ConnectionMock
import org.junit.After
import org.hamcrest.core.StringStartsWith
import org.hamcrest.Matchers
import org.andstatus.app.util.UriUtilsTest
import org.andstatus.app.net.social.activitypub.VerifyCredentialsActivityPubTest
import org.hamcrest.text.IsEmptyString
import org.andstatus.app.net.http.HttpConnectionMock
import org.andstatus.app.tests.R
import org.andstatus.app.util.MyHtmlTest
import org.andstatus.app.net.social.ConnectionGnuSocialTest
import org.andstatus.app.service.AttachmentDownloaderTest
import org.andstatus.app.data.HtmlContentTester
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.note.NoteEditorTest
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.timeline.ListScreenTestHelper
import org.andstatus.app.note.NoteEditorTest.ClipboardReader
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.action.ReplaceTextAction
import org.andstatus.app.util.ScreenshotOnFailure
import io.vavr.control.CheckedRunnable
import android.app.Instrumentation.ActivityMonitor
import org.andstatus.app.service.MyServiceTestHelper
import org.andstatus.app.note.NoteViewItemTest
import org.andstatus.app.note.NoteEditorTwitterTest
import androidx.test.espresso.action.ViewActions
import org.andstatus.app.util.MyLogTest
import org.andstatus.app.util.MyLogTest.LazyClass
import androidx.test.espresso.ViewAction
import android.widget.Checkable
import org.andstatus.app.context.ActivityTest
import android.text.SpannedString
import eu.bolt.screenshotty.ScreenshotManager
import eu.bolt.screenshotty.ScreenshotManagerBuilder
import eu.bolt.screenshotty.ScreenshotResult
import eu.bolt.screenshotty.util.ScreenshotFileSaver
import org.andstatus.app.actor.ActorsScreenTest
import org.andstatus.app.actor.FollowersScreen
import androidx.test.rule.GrantPermissionRule
import org.andstatus.app.backup.MyBackupAgentTest
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import org.andstatus.app.account.MyAccountTest
import org.andstatus.app.context.MyContextTestImpl
import android.app.KeyguardManager
import org.andstatus.app.context.NoScreenSupport
import androidx.test.rule.ActivityTestRule
import org.andstatus.app.context.CompletableFutureTest.TestData
import org.andstatus.app.service.MyServiceTest
import org.andstatus.app.service.AvatarDownloaderTest
import org.andstatus.app.service.RepeatingFailingCommandTest
import org.hamcrest.core.Is
import org.hamcrest.core.IsNot
import org.andstatus.app.timeline.meta.TimelineSyncTrackerTest
import org.andstatus.app.timeline.TimelinePositionTest
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.timeline.TimeLineActivityLayoutToggleTest
import org.andstatus.app.appwidget.MyAppWidgetProviderTest.DateTest
import org.andstatus.app.appwidget.MyAppWidgetProviderTest
import org.andstatus.app.notification.NotifierTest
import org.andstatus.app.ActivityTestHelper.MenuItemClicker
import org.andstatus.app.MenuItemMock

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
class NoteEditorTest : TimelineActivityTest<ActivityViewItem?>() {
    private var data: NoteEditorData? = null
    override fun getActivityIntent(): Intent? {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        if (NoteEditorTest.Companion.editingStep.get() != 1) {
            MyPreferences.setBeingEditedNoteId(0)
        }
        val ma: MyAccount = DemoData.Companion.demoData.getMyAccount(DemoData.Companion.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
        MyContextHolder.Companion.myContextHolder.getNow().accounts().setCurrentAccount(ma)
        data = getStaticData(ma)
        val timeline: Timeline = MyContextHolder.Companion.myContextHolder.getNow().timelines().get(TimelineType.HOME, ma.actor, Origin.Companion.EMPTY)
        MyLog.i(this, "setUp ended, $timeline")
        return Intent(Intent.ACTION_VIEW, timeline.uri)
    }

    private fun getStaticData(ma: MyAccount?): NoteEditorData? {
        return NoteEditorData.Companion.newReplyTo(MyQuery.oidToId(OidEnum.NOTE_OID, ma.getOrigin().id,
                DemoData.Companion.demoData.conversationEntryNoteOid), ma)
                .addToAudience(MyQuery.oidToId(OidEnum.ACTOR_OID, ma.getOrigin().id,
                        DemoData.Companion.demoData.conversationEntryAuthorOid))
                .addMentionsToText()
                .setContent(MyHtmlTest.Companion.twitterBodyTypedPlain + " " + DemoData.Companion.demoData.testRunUid, TextMediaType.PLAIN)
    }

    @Test
    @Throws(InterruptedException::class)
    fun testEditing1() {
        Assert.assertTrue("MyService is available", MyServiceManager.Companion.isServiceAvailable())
        editingTester()
    }

    @Test
    @Throws(InterruptedException::class)
    fun testEditing2() {
        editingTester()
    }

    @Throws(InterruptedException::class)
    private fun editingTester() {
        TestSuite.waitForListLoaded(activity, 2)
        when (NoteEditorTest.Companion.editingStep.incrementAndGet()) {
            2 -> editingStep2()
            else -> {
                NoteEditorTest.Companion.editingStep.set(1)
                ActivityTestHelper.Companion.openEditor<ActivityViewItem?>("default", activity)
                editingStep1()
            }
        }
        MyLog.v(this, "After step " + NoteEditorTest.Companion.editingStep + " ended")
    }

    @Throws(InterruptedException::class)
    private fun editingStep1() {
        val method = "editingStep1"
        MyLog.v(this, "$method started")
        val editorView: View = ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, activity)
        val editor = activity.noteEditor
        instrumentation.runOnMainSync { editor.startEditingNote(data) }
        TestSuite.waitForIdleSync()
        ActivityTestHelper.Companion.waitViewVisible(method, editorView)
        assertInitialText("Initial text")
        MyLog.v(this, "$method ended")
    }

    @Throws(InterruptedException::class)
    private fun editingStep2() {
        val method = "editingStep2"
        MyLog.v(this, "$method started")
        val helper = ActivityTestHelper<TimelineActivity<*>?>(activity)
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.Companion.waitViewVisible("$method; Restored note is visible", editorView)
        assertInitialText("Note restored")
        ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, activity)
        ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, activity)
        NoteEditorTest.Companion.assertTextCleared(this)
        helper.clickMenuItem("$method click Discard", R.id.discardButton)
        ActivityTestHelper.Companion.waitViewInvisible("$method; Editor hidden after discard", editorView)
        MyLog.v(this, "$method ended")
    }

    @Test
    @Throws(InterruptedException::class)
    fun attachOneImage() {
        NoteEditorTest.Companion.attachImages(this, 1, 1)
    }

    @Test
    @Throws(InterruptedException::class)
    fun attachTwoImages() {
        NoteEditorTest.Companion.attachImages(this, 2, 1)
    }

    @Throws(InterruptedException::class)
    private fun assertInitialText(description: String?) {
        val editor = activity.noteEditor
        val textView = activity.findViewById<TextView?>(R.id.noteBodyEditText)
        ActivityTestHelper.Companion.waitTextInAView(description, textView,
                MyHtml.fromContentStored(data.getContent(), TextMediaType.PLAIN))
        Assert.assertEquals(description, data.toTestSummary(), editor.data.toTestSummary())
    }

    /* We see crash in the test...
        java.lang.IllegalStateException: beginBroadcast() called while already in a broadcast
        at android.os.RemoteCallbackList.beginBroadcast(RemoteCallbackList.java:241)
        at com.android.server.clipboard.ClipboardService.setPrimaryClipInternal(ClipboardService.java:583)

        So we split clipboard copying functions into two tests
        ...but this doesn't help...
    */
    @Ignore("We see crash in the test...")
    @Test
    @Throws(InterruptedException::class)
    fun testContextMenuWhileEditing1() {
        val method = "testContextMenuWhileEditing"
        TestSuite.waitForListLoaded(activity, 2)
        ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, activity)
        val helper = ListScreenTestHelper<TimelineActivity<*>?>(activity, ConversationActivity::class.java)
        val listItemId = helper.listItemIdOfLoadedReply
        var logMsg = "listItemId=$listItemId"
        val noteId = if (TimelineType.HOME.showsActivities()) MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId) else listItemId
        logMsg += ", noteId=$noteId"
        val content = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId)
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_TEXT, R.id.note_wrapper)
        Assert.assertEquals(logMsg, content, getClipboardText(method))
    }

    @Ignore("We see crash in the test...")
    @Test
    @Throws(InterruptedException::class)
    fun testContextMenuWhileEditing2() {
        val method = "testContextMenuWhileEditing"
        TestSuite.waitForListLoaded(activity, 2)
        ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, activity)
        val helper = ListScreenTestHelper<TimelineActivity<*>?>(activity, ConversationActivity::class.java)
        val listItemId = helper.listItemIdOfLoadedReply
        val logMsg = "listItemId=$listItemId"
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_AUTHOR, R.id.note_wrapper)
        val text = getClipboardText(method)
        MatcherAssert.assertThat(text, CoreMatchers.startsWith("@"))
        Assert.assertTrue("$logMsg; Text: '$text'", text.startsWith("@") && text.lastIndexOf("@") > 1)
    }

    private fun getClipboardText(methodExt: String?): String? {
        val method = "getClipboardText"
        return try {
            MyLog.v(methodExt, "$method started")
            TestSuite.waitForIdleSync()
            val reader = ClipboardReader()
            instrumentation.runOnMainSync(reader)
            MyLog.v(methodExt, method + "; clip='" + reader.clip + "'")
            if (reader.clip == null) {
                return ""
            }
            val item = reader.clip.getItemAt(0)
            val text = (if (StringUtil.isEmpty(item.htmlText)) item.text else item.htmlText)
                    .toString()
            MyLog.v(methodExt, "$method ended. Text: $text")
            text
        } catch (e: Exception) {
            MyLog.e(method, e)
            "Exception: " + e.message
        }
    }

    private class ClipboardReader : Runnable {
        @Volatile
        var clip: ClipData? = null
        override fun run() {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            val clipboard = MyContextHolder.Companion.myContextHolder.getNow().context()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip = clipboard.primaryClip
        }
    }

    @Test
    @Throws(InterruptedException::class)
    fun editLoadedNote() {
        val method = "editLoadedNote"
        TestSuite.waitForListLoaded(activity, 2)
        val helper = ListScreenTestHelper<TimelineActivity<*>?>(activity,
                ConversationActivity::class.java)
        val listItemId = helper.findListItemId("My loaded note, actorId:" + data.getMyAccount().actorId
        ) { item: BaseNoteViewItem<*>? ->
            (item.author.actorId == data.getMyAccount().actorId
                    && item.noteStatus == DownloadStatus.LOADED)
        }
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId)
        var logMsg = ("itemId=" + listItemId + ", noteId=" + noteId + " text='"
                + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'")
        val invoked = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.EDIT, R.id.note_wrapper)
        logMsg += ";" + if (invoked) "" else " failed to invoke Edit menu item,"
        Assert.assertTrue(logMsg, invoked)
        ActivityTestHelper.Companion.closeContextMenu(activity)
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.Companion.waitViewVisible("$method $logMsg", editorView)
        Assert.assertEquals("Loaded note should be in DRAFT state on Edit start: $logMsg", DownloadStatus.DRAFT,
                getDownloadStatus(noteId))
        val helper2 = ActivityTestHelper<TimelineActivity<*>?>(activity)
        helper2.clickMenuItem("$method clicker Discard $logMsg", R.id.discardButton)
        ActivityTestHelper.Companion.waitViewInvisible("$method $logMsg", editorView)
        Assert.assertEquals("Loaded note should be unchanged after Discard: $logMsg", DownloadStatus.LOADED,
                waitForDownloadStatus(noteId, DownloadStatus.LOADED))
    }

    private fun waitForDownloadStatus(noteId: Long, expected: DownloadStatus?): DownloadStatus? {
        var downloadStatus: DownloadStatus? = DownloadStatus.UNKNOWN
        for (i in 0..29) {
            downloadStatus = getDownloadStatus(noteId)
            if (downloadStatus == expected) return downloadStatus
            DbUtils.waitMs(this, 100)
        }
        return downloadStatus
    }

    private fun getDownloadStatus(noteId: Long): DownloadStatus? {
        return DownloadStatus.Companion.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId))
    }

    @Test
    @Throws(InterruptedException::class)
    fun replying() {
        val method = "replying"
        TestSuite.waitForListLoaded(activity, 2)
        val editorView: View = ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, activity)
        val helper = ListScreenTestHelper<TimelineActivity<*>?>(activity,
                ConversationActivity::class.java)
        val viewItem = helper.findListItem("Some others loaded note"
        ) { item: BaseNoteViewItem<*>? ->
            (item.author.actorId != data.getMyAccount().actorId
                    && item.noteStatus == DownloadStatus.LOADED)
        } as ActivityViewItem
        val listItemId = viewItem.id
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId)
        var logMsg = ("itemId=" + listItemId + ", noteId=" + noteId + " text='"
                + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'")
        Assert.assertEquals(logMsg, viewItem.noteViewItem.id, noteId)
        val invoked = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.REPLY, R.id.note_wrapper)
        logMsg += ";" + if (invoked) "" else " failed to invoke Reply menu item,"
        Assert.assertTrue(logMsg, invoked)
        ActivityTestHelper.Companion.closeContextMenu(activity)
        ActivityTestHelper.Companion.waitViewVisible("$method $logMsg", editorView)
        Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).check(ViewAssertions.matches(ViewMatchers.withText(CoreMatchers.startsWith("@"))))
        TestSuite.waitForIdleSync()
        val content = "Replying to you during " + DemoData.Companion.demoData.testRunUid
        val bodyText = editorView.findViewById<EditText?>(R.id.noteBodyEditText)
        // Espresso types in the centre, unfortunately, so we need to retype text
        Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).perform(ReplaceTextAction(bodyText.text.toString().trim { it <= ' ' }
                + " " + content))
        TestSuite.waitForIdleSync()
        ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>("$method Save draft $logMsg", activity)
        val draftNoteId: Long = ActivityTestHelper.Companion.waitAndGetIdOfStoredNote("$method $logMsg", content)
        Assert.assertEquals("Saved note should be in DRAFT state: $logMsg", DownloadStatus.DRAFT,
                getDownloadStatus(draftNoteId))
        Assert.assertEquals("Wrong id of inReplyTo note of '$content': $logMsg", noteId,
                MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, draftNoteId))
        val audience: Audience = fromNoteId(data.getMyAccount().origin, draftNoteId)
        Assert.assertTrue("Audience of a reply to $viewItem\n $audience",
                audience.findSame(viewItem.noteViewItem.author.actor).isSuccess)
    }

    companion object {
        private val editingStep: AtomicInteger? = AtomicInteger()
        fun attachImages(test: TimelineActivityTest<ActivityViewItem?>?, toAdd: Int, toExpect: Int) {
            ScreenshotOnFailure.screenshotWrapper(test.getActivity()) { NoteEditorTest.Companion._attachImages(test, toAdd, toExpect) }
        }

        @Throws(InterruptedException::class)
        private fun _attachImages(test: TimelineActivityTest<ActivityViewItem?>?, toAdd: Int, toExpect: Int) {
            val method = "attachImages$toAdd"
            MyLog.v(test, "$method started")
            ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, test.getActivity())
            val editorView: View = ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, test.getActivity())
            NoteEditorTest.Companion.assertTextCleared(test)
            TestSuite.waitForIdleSync()
            val noteName = "A note " + toAdd + " " + test.javaClass.simpleName + " can have a title (name)"
            val content = "Note with " + toExpect + " attachment" +
                    (if (toExpect == 1) "" else "s") + " " +
                    DemoData.Companion.demoData.testRunUid
            Espresso.onView(ViewMatchers.withId(R.id.note_name_edit)).perform(ReplaceTextAction(noteName))
            Espresso.onView(ViewMatchers.withId(R.id.note_name_edit)).check(ViewAssertions.matches(ViewMatchers.withText(noteName)))
            Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).perform(ReplaceTextAction(content))
            Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).check(ViewAssertions.matches(ViewMatchers.withText(content)))
            NoteEditorTest.Companion.attachImage(test, editorView, DemoData.Companion.demoData.localImageTestUri2)
            if (toAdd > 1) {
                NoteEditorTest.Companion.attachImage(test, editorView, DemoData.Companion.demoData.localGifTestUri)
            }
            val editor = test.getActivity().noteEditor
            Assert.assertEquals("All image attached " + editor.data.attachedImageFiles, toExpect.toLong(),
                    editor.data.attachedImageFiles.list.size.toLong())
            if (toAdd > 1) {
                val mediaFile = editor.data.attachedImageFiles.list[if (toExpect == 1) 0 else 1]
                Assert.assertEquals("Should be animated $mediaFile", MyContentType.ANIMATED_IMAGE, mediaFile.contentType)
            }
            Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).check(ViewAssertions.matches(ViewMatchers.withText("$content ")))
            Espresso.onView(ViewMatchers.withId(R.id.note_name_edit)).check(ViewAssertions.matches(ViewMatchers.withText(noteName)))
            ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, test.getActivity())
            MyLog.v(test, "$method ended")
        }

        @Throws(InterruptedException::class)
        private fun attachImage(test: TimelineActivityTest<ActivityViewItem?>?, editorView: View?, imageUri: Uri?) {
            val method = "attachImage"
            TestSuite.waitForIdleSync()
            val helper = ActivityTestHelper<TimelineActivity<*>?>(test.getActivity())
            test.getActivity().setSelectorActivityMock(helper)
            helper.clickMenuItem("$method clicker attach_menu_id", R.id.attach_menu_id)
            Assert.assertNotNull(helper.waitForSelectorStart(method, ActivityRequestCode.ATTACH.id))
            test.getActivity().setSelectorActivityMock(null)
            val activityMonitor = test.getInstrumentation()
                    .addMonitor(HelpActivity::class.java.name, null, false)
            val intent1 = Intent(test.getActivity(), HelpActivity::class.java)
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            test.getActivity().applicationContext.startActivity(intent1)
            val selectorActivity = test.getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 25000)
            Assert.assertTrue(selectorActivity != null)
            ActivityTestHelper.Companion.waitViewInvisible(method, editorView)
            DbUtils.waitMs(method, 10000)
            selectorActivity.finish()
            MyLog.i(method, "Callback from a selector")
            val intent2 = Intent()
            intent2.setDataAndType(imageUri, MyContentType.Companion.uri2MimeType(test.getActivity().contentResolver, imageUri,
                    MyContentType.IMAGE.generalMimeType))
            test.getActivity().runOnUiThread { test.getActivity().onActivityResult(ActivityRequestCode.ATTACH.id, Activity.RESULT_OK, intent2) }
            val editor = test.getActivity().noteEditor
            for (attempt in 0..3) {
                ActivityTestHelper.Companion.waitViewVisible(method, editorView)
                // Due to a race the editor may open before this change first.
                if (editor.data.attachedImageFiles.forUri(imageUri).isPresent) {
                    break
                }
                if (DbUtils.waitMs(method, 2000)) {
                    break
                }
            }
            Assert.assertTrue("Image attached", editor.data.attachedImageFiles
                    .forUri(imageUri).isPresent)
        }

        private fun assertTextCleared(test: TimelineActivityTest<ActivityViewItem?>?) {
            val editor = test.getActivity().noteEditor
            Assert.assertTrue("Editor is not null", editor != null)
            Assert.assertEquals(NoteEditorData.Companion.newEmpty(
                    test.getActivity().myContext.accounts().currentAccount).toTestSummary(),
                    editor.data.toTestSummary())
        }
    }
}