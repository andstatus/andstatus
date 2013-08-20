package org.andstatus.app.origin;

import org.andstatus.app.net.Connection.ApiEnum;

public class OriginConnectionData {
    public long originId = 0;
    public ApiEnum api = ApiEnum.UNKNOWN_API;
    public boolean isHttps = true;
    public boolean isOAuth = true;
    public String host = "";
    public String basicPath = "";
    public String oauthPath = "oauth";
    public OAuthClientKeys clientKeys;
}
