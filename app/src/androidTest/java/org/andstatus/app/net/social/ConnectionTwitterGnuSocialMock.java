package org.andstatus.app.net.social;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertTrue;

public class ConnectionTwitterGnuSocialMock extends ConnectionTwitterGnuSocial {

    public ConnectionTwitterGnuSocialMock(ConnectionException e) {
        this();
        getHttpMock().setException(e);
    }

    public ConnectionTwitterGnuSocialMock() {
        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        Origin origin = MyContextHolder.get().persistentOrigins().fromName(demoData.GNUSOCIAL_TEST_ORIGIN_NAME);

        OriginConnectionData connectionData = OriginConnectionData.fromAccountName(
                AccountName.fromOriginAndUserName(origin, demoData.GNUSOCIAL_TEST_ACCOUNT_USERNAME),
                TriState.UNKNOWN);
        connectionData.setAccountUser(demoData.getAccountUserByOid(demoData.GNUSOCIAL_TEST_ACCOUNT_USER_OID));
        connectionData.setDataReader(new AccountDataReaderEmpty());
        enrichConnectionData(connectionData);
        MyAccount ma = MyContextHolder.get().persistentAccounts().getAccountFor(connectionData.getAccountUser());
        assertTrue("Invalid my account " + connectionData.getAccountUser(), ma.isValid());
        try {
            setAccountData(connectionData);
        } catch (ConnectionException e) {
            MyLog.e(this, e);
        }
        TestSuite.setHttpConnectionMockClass(null);
        http.data.originUrl = origin.getUrl();
    }

    public HttpConnectionMock getHttpMock() {
        return (HttpConnectionMock) http;
    }
}
