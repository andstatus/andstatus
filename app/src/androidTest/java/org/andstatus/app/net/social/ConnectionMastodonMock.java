package org.andstatus.app.net.social;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import static org.andstatus.app.context.DemoData.demoData;

public class ConnectionMastodonMock extends ConnectionMastodon implements ConnectionMockable {

    public ConnectionMastodonMock() {
        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        Origin origin = MyContextHolder.get().origins().fromName(demoData.mastodonTestOriginName);

        OriginConnectionData connectionData = OriginConnectionData.fromAccountName(
                AccountName.fromOriginAndUniqueName(origin, demoData.mastodonTestAccountUsername),
                TriState.UNKNOWN);
        connectionData.setAccountActor(demoData.getAccountActorByOid(demoData.mastodonTestAccountActorOid));
        connectionData.setDataReader(new AccountDataReaderEmpty());
        enrichConnectionData(connectionData);
        try {
            setAccountData(connectionData);
        } catch (ConnectionException e) {
            MyLog.e(this, e);
        }
        TestSuite.setHttpConnectionMockClass(null);
        http.data.originUrl = origin.getUrl();
    }
}
