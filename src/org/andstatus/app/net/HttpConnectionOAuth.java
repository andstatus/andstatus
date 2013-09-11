package org.andstatus.app.net;

import android.text.TextUtils;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.net.HttpConnection;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;

abstract class HttpConnectionOAuth extends HttpConnection implements OAuthConsumerAndProvider {
    private static final String TAG = HttpConnectionOAuth.class.getSimpleName();
    
    public static final String USER_TOKEN = "user_token";
    public static final String USER_SECRET = "user_secret";
    public static final String REQUEST_SUCCEEDED = "request_succeeded";
    
    /**
     * Saved User token
     */
    private String userToken;

    /**
     * Saved User secret
     */
    private String userSecret;

    protected HttpConnectionOAuth(OriginConnectionData connectionData_in) {
        connectionData = connectionData_in;
    }
    
    @Override
    public void setAccountData(AccountDataReader dr) {
        super.setAccountData(dr);
        // We look for saved user keys
        if (dr.dataContains(USER_TOKEN) && dr.dataContains(USER_SECRET)) {
            userToken = dr.getDataString(USER_TOKEN, null);
            userSecret = dr.getDataString(USER_SECRET, null);
            setUserTokenWithSecret(userToken, userSecret);
        }
    }
    
    /**
     * @see org.andstatus.app.net.Connection#getCredentialsPresent()
     */
    @Override
    public boolean getCredentialsPresent() {
        boolean yes = false;
        if (connectionData.oauthClientKeys.areKeysPresent()) {
            if (!TextUtils.isEmpty(userToken) && !TextUtils.isEmpty(userSecret)) {
                yes = true;
            }
        }
        return yes;
    }

    @Override
    public boolean isOAuth() {
        return true;
    }
    
    protected String getApiUrl(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case OAUTH_ACCESS_TOKEN:
                url =  connectionData.oauthPath + "/access_token";
                break;
            case OAUTH_AUTHORIZE:
                url = connectionData.oauthPath + "/authorize";
                break;
            case OAUTH_REQUEST_TOKEN:
                url = connectionData.oauthPath + "/request_token";
                break;
            case OAUTH_REGISTER_CLIENT:
                url = connectionData.basicPath + "/client/register";
                break;
            default:
                url = "";
        }
        if (!TextUtils.isEmpty(url)) {
            url = pathToUrl(url);
        }
        return url;
    }

    /**
     * @param token null means to clear the old values
     * @param secret
     */
    @Override
    public void setUserTokenWithSecret(String token, String secret) {
        synchronized (this) {
            userToken = token;
            userSecret = secret;
        }
    }

    @Override
    String getUserToken() {
        return userToken;
    }

    @Override
    String getUserSecret() {
        return userSecret;
    }

    /* (non-Javadoc)
     * @see org.andstatus.app.net.Connection#save(android.content.SharedPreferences, android.content.SharedPreferences.Editor)
     */
    @Override
    public boolean save(AccountDataWriter dw) {
        boolean changed = super.save(dw);

        if ( !TextUtils.equals(userToken, dw.getDataString(USER_TOKEN, null)) ||
                !TextUtils.equals(userSecret, dw.getDataString(USER_SECRET, null)) 
                ) {
            changed = true;

            if (TextUtils.isEmpty(userToken)) {
                dw.setDataString(USER_TOKEN, null);
                MyLog.d(TAG, "Clearing OAuth Token");
            } else {
                dw.setDataString(USER_TOKEN, userToken);
                MyLog.d(TAG, "Saving OAuth Token: " + userToken);
            }
            if (TextUtils.isEmpty(userSecret)) {
                dw.setDataString(USER_SECRET, null);
                MyLog.d(TAG, "Clearing OAuth Secret");
            } else {
                dw.setDataString(USER_SECRET, userSecret);
                MyLog.d(TAG, "Saving OAuth Secret: " + userSecret);
            }
        }
        return changed;
    }

    @Override
    public void clearAuthInformation() {
        setUserTokenWithSecret(null, null);
    }
}
