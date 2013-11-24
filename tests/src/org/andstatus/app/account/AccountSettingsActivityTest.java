package org.andstatus.app.account;

import android.content.Intent;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.test.ActivityInstrumentationTestCase2;

import org.andstatus.app.MyContextHolder;
import org.andstatus.app.MyServiceManager;
import org.andstatus.app.TestSuite;
import org.andstatus.app.data.MyPreferences;

public class AccountSettingsActivityTest extends ActivityInstrumentationTestCase2<AccountSettingsActivity> {
    private AccountSettingsActivity mActivity;
    private MyAccount ma = null;

    public AccountSettingsActivityTest() {
        super(AccountSettingsActivity.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
        TestSuite.enshureDataAdded();
        
        ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        if (ma == null) {
            fail("No persistent accounts yet");
        }
        
        Intent intent = new Intent();
        intent.putExtra(StateOfAccountChangeProcess.EXTRA_MYACCOUNT_GUID, ma.getAccountName());
        setActivityIntent(intent);
        
        mActivity = getActivity();
    }
    
    public void test() throws InterruptedException {
        Preference addAccountOrVerifyCredentials = mActivity.findPreference(MyPreferences.KEY_VERIFY_CREDENTIALS);
        assertTrue(addAccountOrVerifyCredentials != null);
        EditTextPreference usernameText = (EditTextPreference) mActivity.findPreference(MyAccount.Builder.KEY_USERNAME_NEW);
        assertTrue(usernameText != null);
        assertEquals("Selected Username", ma.getUsername(), usernameText.getText());
        Thread.sleep(500);
        assertFalse("MyService is not available", MyServiceManager.isServiceAvailable());
        Thread.sleep(500);
        mActivity.finish();
    }
}
