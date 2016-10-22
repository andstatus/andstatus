package org.andstatus.app.net.http;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.OAuthConfig;

import org.andstatus.app.util.MyLog;

import java.util.Map;

import static org.andstatus.app.net.social.Connection.ApiRoutineEnum.OAUTH_ACCESS_TOKEN;
import static org.andstatus.app.net.social.Connection.ApiRoutineEnum.OAUTH_AUTHORIZE;

public class OAuthApi20 extends DefaultApi20 {
    private final HttpConnectionOAuth http;

    public OAuthApi20(HttpConnectionOAuth http) {
        this.http = http;
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

    @Override
    public String getAuthorizationUrl(OAuthConfig config, Map<String, String> additionalParams) {
        Map<String, String> additionalParams2 = http.getAdditionalAuthorizationParams();
        if (additionalParams != null) {
            if (additionalParams2 == null) {
                additionalParams2 = additionalParams;
            } else {
                additionalParams2.putAll(additionalParams);
            }
        }
        return super.getAuthorizationUrl(config, additionalParams2);
    }
}
