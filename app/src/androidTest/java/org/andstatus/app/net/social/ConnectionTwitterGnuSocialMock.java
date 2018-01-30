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
        Origin origin = MyContextHolder.get().persistentOrigins().fromName(demoData.gnusocialTestOriginName);

        OriginConnectionData connectionData = OriginConnectionData.fromAccountName(
                AccountName.fromOriginAndUsername(origin, demoData.gnusocialTestAccountUsername),
                TriState.UNKNOWN);
        connectionData.setAccountActor(demoData.getAccountActorByOid(demoData.gnusocialTestAccountActorOid));
        connectionData.setDataReader(new AccountDataReaderEmpty());
        enrichConnectionData(connectionData);
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromActorOfSameOrigin(connectionData.getAccountActor());
        assertTrue("Invalid my account " + connectionData.getAccountActor(), ma.isValid());
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
