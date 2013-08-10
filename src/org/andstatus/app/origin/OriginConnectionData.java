package org.andstatus.app.origin;

import org.andstatus.app.net.Connection.ApiEnum;

public class OriginConnectionData {
    public ApiEnum api = ApiEnum.UNKNOWN_API;
    public boolean isHttps = true;
    public boolean isOauth = true;
    public String host = "";
    public String basicPath = "";
    public String oauthPath = "oauth";
    public String consumerKey = "";
    public String consumerSecret = "";
}
