package org.andstatus.app.net;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.json.JSONObject;
import org.json.JSONTokener;

public interface HttpApacheRequest {
    JSONObject postRequest(HttpPost postMethod) throws ConnectionException;
    JSONTokener getRequest(HttpGet get) throws ConnectionException;
    String pathToUrl(String path);
}
