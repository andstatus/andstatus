package org.andstatus.app.net.http;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;

import java.util.Map;

import static org.andstatus.app.net.social.Connection.ApiRoutineEnum.OAUTH_ACCESS_TOKEN;
import static org.andstatus.app.net.social.Connection.ApiRoutineEnum.OAUTH_AUTHORIZE;

public class OAuthApi20 extends DefaultApi20 {
    private final HttpConnectionOAuth http;

    public OAuthApi20(HttpConnectionOAuth http) {
        this.http = http;
    }

    @Override
    public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
        return new MyOAuth2AccessTokenJsonExtractor(http.data);
    }

    @Override
    public String getAccessTokenEndpoint() {
        return http.getApiUri(OAUTH_ACCESS_TOKEN).toString();
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return http.getApiUri(OAUTH_AUTHORIZE).toString();
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
