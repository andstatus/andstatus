package org.andstatus.app.net;

import android.util.Log;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;

import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.OriginConnectionData;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class HttpConnectionOAuthApache extends HttpConnectionOAuth implements HttpApacheRequest {
    private static final String TAG = HttpConnectionOAuth.class.getSimpleName();
    private HttpClient mClient;

    protected HttpConnectionOAuthApache(OriginConnectionData connectionData_in) {
        super(connectionData_in);

        HttpParams parameters = getHttpParams();

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        ClientConnectionManager clientConnectionManager = new ThreadSafeClientConnManager(parameters, schemeRegistry);
        
        mClient = new DefaultHttpClient(clientConnectionManager, parameters);
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

    @Override
    public OAuthProvider getProvider() {
        CommonsHttpOAuthProvider provider = null;
        provider = new CommonsHttpOAuthProvider(getApiUrl(ApiRoutineEnum.OAUTH_REQUEST_TOKEN),
                getApiUrl(ApiRoutineEnum.OAUTH_ACCESS_TOKEN), getApiUrl(ApiRoutineEnum.OAUTH_AUTHORIZE));

        provider.setHttpClient(mClient);
        provider.setOAuth10a(true);
        return provider;
    }
    
    @Override
    protected final JSONObject getRequest(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrl(path));
        return new HttpApacheUtils(this).getRequestAsObject(get);
    }

    @Override
    protected final JSONArray getRequestAsArray(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrl(path));
        return new HttpApacheUtils(this).getRequestAsArray(get);
    }

    @Override
    public JSONTokener getRequest(HttpGet get) throws ConnectionException {
        JSONTokener jso = null;
        String response = null;
        boolean ok = false;
        try {
            if (connectionData.oauthClientKeys.areKeysPresent()) {
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
    public OAuthConsumer getConsumer() {
        OAuthConsumer consumer = new CommonsHttpOAuthConsumer(connectionData.oauthClientKeys.getConsumerKey(),
                connectionData.oauthClientKeys.getConsumerSecret());
        if (getCredentialsPresent()) {
            consumer.setTokenWithSecret(getUserToken(), getUserSecret());
        }
        return consumer;
    }
    
    @Override
    protected final JSONObject postRequest(String path) throws ConnectionException {
        return new HttpApacheUtils(this).postRequest(path);
    }

    @Override
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        return new HttpApacheUtils(this).postRequest(path, jso);
    }
    
    @Override
    public JSONObject postRequest(HttpPost post) throws ConnectionException {
        JSONObject jso = null;
        String response = null;
        boolean ok = false;
        try {
            // Maybe we'll need this:
            // post.setParams(...);

            if (connectionData.oauthClientKeys.areKeysPresent()) {
                // sign the request to authenticate
                getConsumer().sign(post);
            }
            response = mClient.execute(post, new BasicResponseHandler());
            jso = new JSONObject(response);
            ok = true;
        } catch (HttpResponseException e) {
            ConnectionException e2 = ConnectionException.fromStatusCodeHttp(e.getStatusCode(), e.getLocalizedMessage());
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
