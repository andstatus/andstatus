package org.andstatus.app;

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.ViewFlipper;

import org.andstatus.app.TestSuite;

public class HelpActivityTest extends ActivityInstrumentationTestCase2<HelpActivity> {
    private HelpActivity mActivity;

    public HelpActivityTest() {
        super(HelpActivity.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
        
        Intent intent = new Intent();
        intent.putExtra(HelpActivity.EXTRA_IS_FIRST_ACTIVITY, true);
        intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_ID, HelpActivity.HELP_PAGE_CHANGELOG);
        setActivityIntent(intent);
        
        mActivity = getActivity();
    }
    
    public void test() throws InterruptedException {
        ViewFlipper mFlipper = ((ViewFlipper) mActivity.findViewById(R.id.help_flipper));
        assertTrue(mFlipper != null);
        assertEquals("At Changelog page", HelpActivity.HELP_PAGE_CHANGELOG, mFlipper.getDisplayedChild());
        View changeLogView = mActivity.findViewById(R.id.help_changelog);
        assertTrue(changeLogView != null);
        
        Thread.sleep(500);
        mActivity.finish();
    }
}
