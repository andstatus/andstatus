package org.andstatus.app.net.http;

import com.github.scribejava.core.builder.api.DefaultApi20;

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
            return http.getApiUri(OAUTH_ACCESS_TOKEN).toString();
        } catch (ConnectionException e) {
            MyLog.e(this, e);
        }
        return "";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        try {
            return http.getApiUri(OAUTH_AUTHORIZE).toString();
        } catch (ConnectionException e) {
            MyLog.e(this, e);
        }
        return "";
    }

    @Override
    public String getAuthorizationUrl(String responseType, String apiKey, String callback, String scope, String state,
                                      Map<String, String> additionalParams) {
        Map<String, String> additionalParams2 = http.getAdditionalAuthorizationParams();
        if (additionalParams != null) {
            if (additionalParams2 == null) {
                additionalParams2 = additionalParams;
            } else {
                additionalParams2.putAll(additionalParams);
            }
        }
        return super.getAuthorizationUrl(responseType, apiKey, callback, scope, state, additionalParams2);
    }
}
