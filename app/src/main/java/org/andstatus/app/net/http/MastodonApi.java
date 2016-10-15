package org.andstatus.app.net.http;

import com.github.scribejava.core.builder.api.DefaultApi20;

import org.andstatus.app.util.MyLog;

import static org.andstatus.app.net.social.Connection.ApiRoutineEnum.OAUTH_ACCESS_TOKEN;
import static org.andstatus.app.net.social.Connection.ApiRoutineEnum.OAUTH_AUTHORIZE;

public class MastodonApi extends DefaultApi20 {
    private final HttpConnectionOAuth http;

    private MastodonApi(HttpConnectionOAuth http) {
        this.http = http;
    }

    public static MastodonApi instance(HttpConnectionOAuth http) {
        return new MastodonApi(http);
    }

    @Override
    public String getAccessTokenEndpoint() {
        try {
            return http.getApiUrl(OAUTH_ACCESS_TOKEN);
        } catch (ConnectionException e) {
            MyLog.e(this, e);
        }
        return "";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        try {
            return http.getApiUrl(OAUTH_AUTHORIZE);
        } catch (ConnectionException e) {
            MyLog.e(this, e);
        }
        return "";
    }
}
