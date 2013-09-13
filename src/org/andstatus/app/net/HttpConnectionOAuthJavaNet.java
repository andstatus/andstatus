package org.andstatus.app.net;

import android.text.TextUtils;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;

import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpConnectionOAuthJavaNet extends HttpConnectionOAuth {
    private static final String TAG = HttpConnectionOAuthJavaNet.class.getSimpleName();

    protected HttpConnectionOAuthJavaNet(OriginConnectionData connectionData_in) {
        super(connectionData_in);
    }

    @Override
    public OAuthProvider getProvider() {
        OAuthProvider provider = null;
        provider = new DefaultOAuthProvider(getApiUrl(ApiRoutineEnum.OAUTH_REQUEST_TOKEN),
                getApiUrl(ApiRoutineEnum.OAUTH_ACCESS_TOKEN), getApiUrl(ApiRoutineEnum.OAUTH_AUTHORIZE));
        provider.setOAuth10a(true);
        return provider;

    }

    @Override
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        JSONObject result = null;
        try {
            MyLog.v(TAG, "Posting " + (jso == null ? "(empty)" : jso.toString(2)));
        
            URL url = new URL(pathToUrl(path));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            getConsumer().sign(conn);
            
            if (jso != null) {
                OutputStream os = conn.getOutputStream();
                OutputStreamWriter wr = new OutputStreamWriter(os);
                String toWrite = jso.toString(); 
                wr.write(toWrite);
                try {
                    wr.close();
                } catch (IOException e) {
                    MyLog.v(TAG, "Error closing output stream: " + e);
                }
            }
                        
            int responseCode = conn.getResponseCode();
            switch(responseCode) {
                case 200:
                    result = new JSONObject(HttpJavaNetUtils.readAll(conn.getInputStream()));
                    break;
                default:
                    String responseString = HttpJavaNetUtils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                    try {
                        JSONObject jsonError = new JSONObject(responseString);
                        String error = jsonError.optString("error");
                        StatusCode statusCode = (error.indexOf("not found") < 0 ? StatusCode.UNKNOWN : StatusCode.NOT_FOUND);
                        throw new ConnectionException(statusCode, "Error getting '" + path + "', status=" + responseCode + ", error='" + error + "'");
                    } catch (JSONException e) {
                        throw new ConnectionException("Error getting '" + path + "', status=" + responseCode + ", non-JSON response: '" + responseString + "'");
                    }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, result, "Error getting '" + path + "'");
        } catch (ConnectionException e) {
            throw e;
        } catch(Exception e) {
            throw new ConnectionException("Error getting '" + path + "', " + e.toString());
        }
        return result;
    }

    @Override public OAuthConsumer getConsumer() {
        OAuthConsumer consumer = new DefaultOAuthConsumer(
                connectionData.oauthClientKeys.getConsumerKey(),
                connectionData.oauthClientKeys.getConsumerSecret());
        if (getCredentialsPresent()) {
            consumer.setTokenWithSecret(getUserToken(), getUserSecret());
        }
        return consumer;
    }
    
    @Override
    protected JSONObject postRequest(String path) throws ConnectionException {
        return postRequest(path, null);
    }

    @Override
    protected JSONObject getRequest(String path) throws ConnectionException {
        if (TextUtils.isEmpty(path)) {
            throw new IllegalArgumentException("path is empty");
        }
        String responseString = "";
        JSONObject result = null;
        try {
            OAuthConsumer consumer = getConsumer();
            
            URL url = new URL(pathToUrl(path));
            HttpURLConnection conn;
            for (boolean done=false; !done; ) {
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                consumer.sign(conn);
                conn.connect();
                int responseCode = conn.getResponseCode();
                StatusCode statusCode = StatusCode.fromResponseCode(responseCode);
                switch(responseCode) {
                    case 200:
                        try {
                            responseString = HttpJavaNetUtils.readAll(conn.getInputStream());
                            result = new JSONObject(responseString);
                            done = true;
                        } catch (JSONException e) {
                            throw new ConnectionException(statusCode, "Error reading response from '" + path + "', status=" + responseCode + ", non-JSON response: '" + responseString + "'");
                        }
                        break;
                    case 301:
                    case 302:
                    case 303:
                    case 307:
                        url = new URL(conn.getHeaderField("Location"));
                        MyLog.v(TAG, "Following redirect to " + url);
                        break;                        
                    default:
                        responseString = HttpJavaNetUtils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                        try {
                            JSONObject jsonError = new JSONObject(responseString);
                            String error = jsonError.optString("error");
                            if (statusCode == StatusCode.UNKNOWN) {
                                statusCode = (error.indexOf("not found") < 0 ? StatusCode.UNKNOWN : StatusCode.NOT_FOUND);
                            }
                            throw new ConnectionException(statusCode, "Error getting '" + path + "', status=" + responseCode + ", error='" + error + "'");
                        } catch (JSONException e) {
                            throw new ConnectionException(statusCode, "Error getting '" + path + "', status=" + responseCode + ", non-JSON response: '" + responseString + "'");
                        }
                }
            }
        } catch (ConnectionException e) {
            throw e;
        } catch(Exception e) {
            throw new ConnectionException("Error getting '" + path + "', " + e.toString());
        }
        return result;
    }

    @Override
    protected JSONArray getRequestAsArray(String path) throws ConnectionException {
        JSONObject jso = getRequest(path);
        JSONArray jsa = null;
        if (jso == null) {
            throw new ConnectionException("Response is null");
        }
        if (jso.has("items")) {
            try {
                jsa = jso.getJSONArray("items");
            } catch (JSONException e) {
                throw new ConnectionException("'items' is not an array?!");
            }
        } else {
            try {
                MyLog.d(TAG, "Response from server: " + jso.toString(4));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            throw new ConnectionException("No array was returned");
        }
        return jsa;
    }
}
