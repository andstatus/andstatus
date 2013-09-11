package org.andstatus.app.net;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.origin.OriginConnectionData;
import org.json.JSONArray;
import org.json.JSONObject;

abstract class HttpConnection {
    protected static final Integer DEFAULT_GET_REQUEST_TIMEOUT = 15000;
    protected static final Integer DEFAULT_POST_REQUEST_TIMEOUT = 20000;
    
    protected OriginConnectionData connectionData;

    static final String USER_AGENT = "AndStatus";
 
    protected abstract JSONObject postRequest(String path, JSONObject jso) throws ConnectionException;
    
    public String pathToUrl(String path) {
        if (path.contains("://")) {
            return path;
        } else {
            return "http" + (connectionData.isHttps ? "s" : "")
                    + "://" + connectionData.host
                    + "/" + path;
        }
    }
    
    protected abstract JSONObject postRequest(String path) throws ConnectionException;
    
    protected abstract JSONObject getRequest(String path) throws ConnectionException;
    protected abstract JSONArray getRequestAsArray(String path) throws ConnectionException;
    
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
