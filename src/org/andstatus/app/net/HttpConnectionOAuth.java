package org.andstatus.app.net;

import android.text.TextUtils;
import android.util.Log;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.net.HttpConnection;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

class HttpConnectionOAuth extends HttpConnection implements OAuthConsumerAndProvider {
    private static final String TAG = HttpConnectionOAuth.class.getSimpleName();
    
    public static final String USER_TOKEN = "user_token";
    public static final String USER_SECRET = "user_secret";
    public static final String REQUEST_SUCCEEDED = "request_succeeded";
    
    private OAuthConsumer mConsumer = null;
    private CommonsHttpOAuthProvider mProvider = null;

    /**
     * Saved User token
     */
    private String userToken;

    /**
     * Saved User secret
     */
    private String userSecret;

    private HttpClient mClient;
    
    protected HttpConnectionOAuth(OriginConnectionData connectionData_in) {
        connectionData = connectionData_in;

        HttpParams parameters = getHttpParams();

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        ClientConnectionManager clientConnectionManager = new ThreadSafeClientConnManager(parameters, schemeRegistry);
        mClient = new DefaultHttpClient(clientConnectionManager, parameters);

        mConsumer = new CommonsHttpOAuthConsumer(connectionData.clientKeys.getConsumerKey(),
                connectionData.clientKeys.getConsumerSecret());

        mProvider = new CommonsHttpOAuthProvider(getApiUrl(ApiRoutineEnum.OAUTH_REQUEST_TOKEN),
                getApiUrl(ApiRoutineEnum.OAUTH_ACCESS_TOKEN), getApiUrl(ApiRoutineEnum.OAUTH_AUTHORIZE));

        mProvider.setHttpClient(mClient);
        // It turns out this was the missing thing to making standard
        // Activity launch mode work
        // TODO: Decide if this is necessary
        mProvider.setOAuth10a(true);
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

    private HttpParams getHttpParams() {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
        HttpConnectionParams.setStaleCheckingEnabled(params, true);

        HttpProtocolParams.setUseExpectContinue(params, false);
        // HttpConnectionParams.setTcpNoDelay(parameters, true);
        HttpConnectionParams.setSoTimeout(params, 30000);
        HttpConnectionParams.setSocketBufferSize(params, 2*8192);
        return params;
    }
    
    /**
     * @see org.andstatus.app.net.Connection#getCredentialsPresent()
     */
    @Override
    public boolean getCredentialsPresent() {
        boolean yes = false;
        if (connectionData.clientKeys.areKeysPresent()) {
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

    @Override
    public OAuthConsumer getConsumer() {
        return mConsumer;
    }

    @Override
    public OAuthProvider getProvider() {
        return mProvider;
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
    public void setUserTokenWithSecret(String token, String secret) {
        synchronized (this) {
            userToken = token;
            userSecret = secret;
            if (!(userToken == null || userSecret == null)) {
                getConsumer().setTokenWithSecret(userToken, userSecret);
            }
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

    @Override
    protected JSONTokener getRequest(HttpGet get) throws ConnectionException {
        JSONTokener jso = null;
        String response = null;
        boolean ok = false;
        try {
            if (connectionData.clientKeys.areKeysPresent()) {
                getConsumer().sign(get);
            }
            response = mClient.execute(get, new BasicResponseHandler());
            jso = new JSONTokener(response);
            ok = true;
        } catch (Exception e) {
            Log.e(TAG, "Exception was caught, URL='" + get.getURI().toString() + "'");
            e.printStackTrace();
            throw new ConnectionException(e.getLocalizedMessage());
        }
        if (!ok) {
            jso = null;
        }
        return jso;
    }

    @Override
    protected JSONObject postRequest(HttpPost post) throws ConnectionException {
        JSONObject jso = null;
        String response = null;
        boolean ok = false;
        try {
            // Maybe we'll need this:
            // post.setParams(...);

            if (connectionData.clientKeys.areKeysPresent()) {
                // sign the request to authenticate
                getConsumer().sign(post);
            }
            response = mClient.execute(post, new BasicResponseHandler());
            jso = new JSONObject(response);
            ok = true;
        } catch (HttpResponseException e) {
            ConnectionException e2 = new ConnectionException(e.getStatusCode(), e.getLocalizedMessage());
            Log.w(TAG, e2.getLocalizedMessage());
            throw e2;
        } catch (JSONException e) {
            Log.w(TAG, "postRequest, response=" + (response == null ? "(null)" : response));
            throw new ConnectionException(e.getLocalizedMessage());
        } catch (Exception e) {
            // We don't catch other exceptions because in fact it's vary difficult to tell
            // what was a real cause of it. So let's make code clearer.
            e.printStackTrace();
            throw new ConnectionException(e.getLocalizedMessage());
        }
        if (!ok) {
            jso = null;
        }
        return jso;
    }
    
}
