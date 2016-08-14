package org.andstatus.app.context;

import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.test.ActivityInstrumentationTestCase2;

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
        if (fragment != null) {
            Preference preference = fragment.findPreference(
                    MySettingsFragment.KEY_MANAGE_ACCOUNTS);
            assertTrue(MySettingsFragment.KEY_MANAGE_ACCOUNTS, preference != null);
        }
        Thread.sleep(500);
        assertFalse("MyService is not available", MyServiceManager.isServiceAvailable());
    }
}
