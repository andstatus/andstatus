package org.andstatus.app.context;

import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.test.ActivityInstrumentationTestCase2;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.service.MyServiceManager;

public class MySettingsActivityTest extends ActivityInstrumentationTestCase2<MySettingsActivity> {
    private MySettingsActivity mActivity;

    public MySettingsActivityTest() {
        super(MySettingsActivity.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
        
        mActivity = getActivity();
    }
    
    public void test() throws InterruptedException {
        PreferenceFragment fragment = (PreferenceFragment) mActivity.getFragmentManager().findFragmentByTag(MySettingsFragment.class.getSimpleName());
        CheckBoxPreference mUseExternalStorage = (CheckBoxPreference) fragment.findPreference(
                MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW);
        assertTrue(mUseExternalStorage != null);
        Thread.sleep(500);
        assertFalse("MyService is not available", MyServiceManager.isServiceAvailable());
    }
}
