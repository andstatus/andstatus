package org.andstatus.app.net.social.pumpio;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UrlUtils;

/**
 *
 */
class ConnectionAndUrl {
    public final String url;
    public final HttpConnection httpConnection;

    public ConnectionAndUrl(String url, HttpConnection httpConnection) {
        this.url = url;
        this.httpConnection = httpConnection;
    }

    public static ConnectionAndUrl getConnectionAndUrl(ConnectionPumpio connection, Connection.ApiRoutineEnum apiRoutine, Actor actor) throws ConnectionException {
        if (actor == null || StringUtils.isEmpty(actor.oid)) {
            throw new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine + ": actorId is required");
        }
        return  getConnectionAndUrlForActor(connection, apiRoutine, actor);
    }

    public static ConnectionAndUrl getConnectionAndUrlForActor(ConnectionPumpio connection, Connection.ApiRoutineEnum apiRoutine, Actor actor) throws ConnectionException {
        String username = actor.getUsername();
        if (StringUtils.isEmpty(username)) {
            throw new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine + ": username is required");
        }
        String nickname = connection.usernameToNickname(username);
        if (StringUtils.isEmpty(nickname)) {
            throw new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine + ": wrong username='" + username + "'");
        }
        String url = connection.getApiPath(apiRoutine).replace("%nickname%", nickname);
        HttpConnection httpConnection = connection.getHttp();
        String host = actor.getHost();
        if (StringUtils.isEmpty(host)) {
            throw new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine + ": host is empty for " + actor);
        } else if (connection.getHttp().data.originUrl == null || host.compareToIgnoreCase(connection.getHttp().data.originUrl.getHost()) != 0) {
            MyLog.v(connection, () -> "Requesting data from the host: " + host);
            HttpConnectionData connectionData1 = connection.getHttp().data.copy();
            connectionData1.oauthClientKeys = null;
            connectionData1.originUrl = UrlUtils.buildUrl(host, connectionData1.isSsl());
            httpConnection = connection.getHttp().getNewInstance();
            httpConnection.setConnectionData(connectionData1);
        }
        if (!httpConnection.data.areOAuthClientKeysPresent()) {
            httpConnection.registerClient();
            if (!httpConnection.getCredentialsPresent()) {
                throw ConnectionException.fromStatusCodeAndHost(ConnectionException.StatusCode.NO_CREDENTIALS_FOR_HOST,
                        "No credentials", httpConnection.data.originUrl);
            }
        }
        return new ConnectionAndUrl(url, httpConnection);
    }
}
