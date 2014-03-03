package org.andstatus.app.context;

import android.preference.CheckBoxPreference;
import android.test.ActivityInstrumentationTestCase2;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.service.MyServiceManager;

public class MyPreferenceActivityTest extends ActivityInstrumentationTestCase2<MyPreferenceActivity> {
    private MyPreferenceActivity mActivity;

    public MyPreferenceActivityTest() {
        super(MyPreferenceActivity.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
        
        mActivity = getActivity();
    }
    
    public void test() throws InterruptedException {
        CheckBoxPreference mUseExternalStorage = (CheckBoxPreference) mActivity.getPreferenceScreen().findPreference(
                MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW);
        assertTrue(mUseExternalStorage != null);
        Thread.sleep(500);
        assertFalse("MyService is not available", MyServiceManager.isServiceAvailable());
    }
}
