package org.andstatus.app.net;

import android.util.Log;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.origin.OriginConnectionData;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.util.List;

abstract class HttpConnection {
    private static final String TAG = HttpConnection.class.getSimpleName();
    
    protected static final Integer DEFAULT_GET_REQUEST_TIMEOUT = 15000;
    protected static final Integer DEFAULT_POST_REQUEST_TIMEOUT = 20000;
    
    protected OriginConnectionData connectionData;

    static final String USER_AGENT = "AndStatus";

    public static HttpConnection fromConnectionData(OriginConnectionData connectionData) {
        HttpConnection connection;
        if (connectionData.isOAuth) {
            connection = new HttpConnectionOAuth(connectionData);
        } else {
            connection = new HttpConnectionBasic(connectionData);
        }
        return connection;
    }
    
    /**
     * @throws ConnectionException
     */
    protected final JSONObject postRequest(String path, List<NameValuePair> formParams) throws ConnectionException {
        JSONObject jso = null;
        HttpPost postMethod = new HttpPost(pathToUrl(path));
        try {
            if (formParams != null) {
                UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(formParams, HTTP.UTF_8);
                postMethod.setEntity(formEntity);
            }
            jso = postRequest(postMethod);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
        }
        return jso;
    }
    
    public String pathToUrl(String path) {
        return "http" + (connectionData.isHttps ? "s" : "")
                + "://" + connectionData.host
                + "/" + path;
    }
    
    protected final JSONObject postRequest(String path) throws ConnectionException {
        HttpPost post = new HttpPost(pathToUrl(path));
        return postRequest(post);
    }
    
    protected abstract JSONObject postRequest(HttpPost post) throws ConnectionException;
    
    protected final JSONObject getRequest(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrl(path));
        return getRequestAsObject(get);
    }

    protected final JSONArray getRequestAsArray(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrl(path));
        return getRequestAsArray(get);
    }
    
    private final JSONArray getRequestAsArray(HttpGet get) throws ConnectionException {
        JSONArray jsa = null;
        JSONTokener jst = getRequest(get);
        try {
            jsa = (JSONArray) jst.nextValue();
        } catch (JSONException e) {
            Log.w(TAG, "getRequestAsArray, JSONException response=" + (jst == null ? "(null)" : jst.toString()));
            throw new ConnectionException(e.getLocalizedMessage());
        } catch (ClassCastException e) {
            Log.w(TAG, "getRequestAsArray, ClassCastException response=" + (jst == null ? "(null)" : jst.toString()));
            throw new ConnectionException(e.getLocalizedMessage());
        }
        return jsa;
    }

    private final JSONObject getRequestAsObject(HttpGet get) throws ConnectionException {
        JSONObject jso = null;
        JSONTokener jst = getRequest(get);
        try {
            jso = (JSONObject) jst.nextValue();
        } catch (JSONException e) {
            Log.w(TAG, "getRequestAsObject, JSONException response=" + (jst == null ? "(null)" : jst.toString()));
            throw new ConnectionException(e.getLocalizedMessage());
        } catch (ClassCastException e) {
            Log.w(TAG, "getRequestAsObject, ClassCastException response=" + (jst == null ? "(null)" : jst.toString()));
            throw new ConnectionException(e.getLocalizedMessage());
        }
        return jso;
    }
    
    protected abstract JSONTokener getRequest(HttpGet get) throws ConnectionException;

    public abstract void clearAuthInformation();

    public void setAccountData(AccountDataReader dr) {}
    
    protected boolean isOAuth() {
        return connectionData.isOAuth;
    }
    

    /**
     * Do we need password to be set?
     * By default password is not needed and is ignored
     */
    public boolean isPasswordNeeded() {
        return false;
    }
    
    /**
     * Set User's password if the Connection object needs it
     */
    public void setPassword(String password) { }

    
    public String getPassword() {
        return "";
    }
    
    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    public boolean save(AccountDataWriter dw) {
        boolean changed = false;

        // Nothing to save
        
        return changed;
    }
    
    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    public abstract boolean getCredentialsPresent();

    public void setUserTokenWithSecret(String token, String secret) {
        throw(new IllegalArgumentException("setUserTokenWithSecret is for OAuth only!"));
    }

    String getUserToken() {
        return "";
    }

    String getUserSecret() {
        return "";
    }  
    
}
