package org.andstatus.app.timeline

import android.content.Intent
import android.net.Uri
import android.provider.BaseColumns
import android.view.View
import android.widget.TextView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ReplaceTextAction
import androidx.test.espresso.matcher.ViewMatchers
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.service.MyServiceTestHelper
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.view.SelectorDialog
import org.junit.After
import org.junit.Assert
import org.junit.Test
import kotlin.properties.Delegates

class SharingMediaToThisAppTest : TimelineActivityTest<ActivityViewItem>() {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private var mService: MyServiceTestHelper by Delegates.notNull()
    private var ma: MyAccount = MyAccount.EMPTY

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        mService = MyServiceTestHelper()
        mService.setUp(DemoData.demoData.gnusocialTestAccountName)
        ma = DemoData.demoData.getGnuSocialAccount()
        Assert.assertTrue(ma.isValid)
        myContext.accounts.setCurrentAccount(ma)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "image/png"
        val mediaUri: Uri = DemoData.demoData.localImageTestUri2
        Assert.assertNotNull(mediaUri)
        intent.putExtra(Intent.EXTRA_STREAM, mediaUri)
        MyLog.i(this, "setUp ended")
        return intent
    }

    @After
    fun tearDown() {
        mService.tearDown()
    }

    @Test
    fun testSharingMediaToThisApp() {
        val method = "testSharingMediaToThisApp"
        TestSuite.waitForListLoaded(activity, 4)

        val listActivityTestHelper: ListActivityTestHelper<TimelineActivity<*>> =
            ListActivityTestHelper.newForSelectorDialog(activity, SelectorDialog.Companion.dialogTag)
        listActivityTestHelper.selectIdFromSelectorDialog(method, ma.actorId)
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.waitViewVisible(method, editorView)
        val details = editorView.findViewById<TextView?>(R.id.noteEditDetails)
        val textToFind: String = myContext.context.getText(R.string.label_with_media).toString()
        ActivityTestHelper.waitTextInAView(method, details, textToFind)
        EspressoUtils.waitForIdleSync()
        val content = "Test note with a shared image " + DemoData.demoData.testRunUid
        Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).perform(ReplaceTextAction(content))
        EspressoUtils.waitForIdleSync()

        ActivityTestHelper.clickSendButton(method, activity)
        val found = mService.waitForCondition {
            getHttp().substring2PostedPath("statuses/update").isNotEmpty()
        }
        val message = ("Data was posted " + mService.getHttp().getPostedCounter() + " times; " +
            mService.getHttp().getResults().toTypedArray().contentToString())
        MyLog.v(this, "$method; $message")
        Assert.assertTrue(message, mService.getHttp().getPostedCounter() > 0)
        Assert.assertTrue(message, found)

        val condition = NoteTable.CONTENT + "='" + content + "'"
        val unsentMsgId = MyQuery.conditionToLongColumnValue(NoteTable.TABLE_NAME, BaseColumns._ID, condition)
        Assert.assertTrue("Unsent note found: $condition", unsentMsgId != 0L)
        Assert.assertEquals(
            "Status of unsent note", DownloadStatus.SENDING, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, unsentMsgId)
            )
        )
        val dd: DownloadData = DownloadData.Companion.getSingleAttachment(unsentMsgId)
        MyLog.v(this, "$method; $dd")
        Assert.assertEquals("Image URI stored", DemoData.demoData.localImageTestUri2, dd.getUri())
        Assert.assertEquals("Loaded '" + dd.getUri() + "'; " + dd, DownloadStatus.LOADED, dd.getStatus())
    }
}
