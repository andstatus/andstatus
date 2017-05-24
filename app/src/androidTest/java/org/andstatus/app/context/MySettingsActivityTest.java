package org.andstatus.app.context;

import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.service.MyServiceManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MySettingsActivityTest extends ActivityTest<MySettingsActivity> {

    @Override
    protected Class<MySettingsActivity> getActivityClass() {
        return MySettingsActivity.class;
    }

    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
    }

    @Test
    public void test() throws InterruptedException {
        final String method = "test";
        PreferenceFragment fragment = (PreferenceFragment) getActivity().getFragmentManager().findFragmentByTag
                (MySettingsFragment.class.getSimpleName());
        if (fragment != null) {
            Preference preference = fragment.findPreference(
                    MySettingsFragment.KEY_MANAGE_ACCOUNTS);
            assertTrue(MySettingsFragment.KEY_MANAGE_ACCOUNTS, preference != null);
        }
        DbUtils.waitMs(method, 500);
        assertFalse("MyService is not available", MyServiceManager.isServiceAvailable());
    }
}
