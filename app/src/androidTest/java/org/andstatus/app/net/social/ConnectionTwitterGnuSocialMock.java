package org.andstatus.app.net.social;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

public class ConnectionTwitterGnuSocialMock extends ConnectionTwitterGnuSocial {

    public ConnectionTwitterGnuSocialMock(ConnectionException e) {
        this();
        getHttpMock().setException(e);
    }

    public ConnectionTwitterGnuSocialMock() {
        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME);
        
        OriginConnectionData connectionData = origin.getConnectionData(TriState.UNKNOWN);
        connectionData.setAccountUserOid(TestSuite.GNUSOCIAL_TEST_ACCOUNT_USER_OID);
        connectionData.setAccountUsername(TestSuite.GNUSOCIAL_TEST_ACCOUNT_USERNAME);
        connectionData.setDataReader(new AccountDataReaderEmpty());
        enrichConnectionData(connectionData);
        connectionData.setHttpConnectionClass(HttpConnectionMock.class);
        try {
            setAccountData(connectionData);
        } catch (InstantiationException e) {
            MyLog.e(this, e);
        } catch (IllegalAccessException e) {
            MyLog.e(this, e);
        }
        http.data.originUrl = origin.getUrl();
    }

    public HttpConnectionMock getHttpMock() {
        return (HttpConnectionMock) http;
    }
}
