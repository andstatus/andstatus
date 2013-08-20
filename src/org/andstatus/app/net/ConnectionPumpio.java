package org.andstatus.app.net;

import android.text.TextUtils;
import android.util.Log;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;

import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of pump.io API: <a href="https://github.com/e14n/pump.io/blob/master/API.md">https://github.com/e14n/pump.io/blob/master/API.md</a>  
 * @author yvolk
 */
class ConnectionPumpio extends Connection {
    private static final String TAG = ConnectionPumpio.class.getSimpleName();

    public ConnectionPumpio(OriginConnectionData connectionData) {
        super(connectionData);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void registerClient() {
        registerClientAttempt1();
        if (httpConnection.connectionData.clientKeys.areKeysPresent()) {
            MyLog.v(TAG, "Registered client for " + httpConnection.connectionData.host);
        }
    }

    /**
     * It works!
     */
    public void registerClientAttempt1() {
        String consumerKey = "";
        String consumerSecret = "";
        httpConnection.connectionData.clientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret);

        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        formParams.add(new BasicNameValuePair("type", "client_associate"));
        formParams.add(new BasicNameValuePair("application_type", "native"));
        formParams.add(new BasicNameValuePair("redirect_uris", AccountSettingsActivity.CALLBACK_URI.toString()));
        formParams.add(new BasicNameValuePair("application_name", HttpConnection.USER_AGENT));
        try {
            JSONObject jso = postRequest(ApiRoutineEnum.REGISTER_CLIENT, formParams);
            if (jso != null) {
                consumerKey = jso.getString("client_id");
                consumerSecret = jso.getString("client_secret");
                httpConnection.connectionData.clientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret);
            }
        } catch (ConnectionException e) {
            Log.e(TAG, "registerClient Exception: " + e.toString());
        } catch (JSONException e) {
            Log.e(TAG, "registerClient Exception: " + e.toString());
            e.printStackTrace();
        }
    }
    
    /**
     * It works also: partially borrowed from the "Impeller" code !
     */
    public void registerClientAttempt2() {
        String consumerKey = "";
        String consumerSecret = "";
        httpConnection.connectionData.clientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret);

        try {
            URL endpoint = new URL(httpConnection.pathToUrl(getApiPath(ApiRoutineEnum.REGISTER_CLIENT)));
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
                    
            HashMap<String, String> params = new HashMap<String, String>();
            params.put("type", "client_associate");
            params.put("application_type", "native");
            params.put("redirect_uris", AccountSettingsActivity.CALLBACK_URI.toString());
            params.put("client_name", HttpConnection.USER_AGENT);
            params.put("application_name", HttpConnection.USER_AGENT);
            String requestBody = encode(params);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            
            Writer w = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
            w.write(requestBody);
            w.close();
            
            if(conn.getResponseCode() != 200) {
                String msg = readAll(new InputStreamReader(conn.getErrorStream()));
                Log.e(TAG, "Server returned an error response: " + msg);
                Log.e(TAG, "Server returned an error response: " + conn.getResponseMessage());
            } else {
                String response = readAll(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                JSONObject jso = new JSONObject(response);
                if (jso != null) {
                    consumerKey = jso.getString("client_id");
                    consumerSecret = jso.getString("client_secret");
                    httpConnection.connectionData.clientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "registerClient Exception: " + e.toString());
        } catch (JSONException e) {
            Log.e(TAG, "registerClient Exception: " + e.toString());
            e.printStackTrace();
        }
    }
    
    static private String encode(Map<String, String> params) {
        try {
            StringBuilder sb = new StringBuilder();
            for(Map.Entry<String, String> entry : params.entrySet()) {
                if(sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            
            return sb.toString();
        } catch(UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }

    static private String readAll(InputStream s) throws IOException {
        return readAll(new InputStreamReader(s, "UTF-8"));
    }
    
    static private String readAll(Reader r) throws IOException {
        int nRead;
        char[] buf = new char[16 * 1024];
        StringBuilder bld = new StringBuilder();
        while((nRead = r.read(buf)) != -1) {
            bld.append(buf, 0, nRead);
        }
        return bld.toString();
    }
    
    protected static Connection fromConnectionDataProtected(OriginConnectionData connectionData) {
        return new ConnectionPumpio(connectionData);
    }

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "whoami";
                break;
            case REGISTER_CLIENT:
                url = "client/register";
                break;
            default:
                url = "";
        }
        if (!TextUtils.isEmpty(url)) {
            url = httpConnection.connectionData.basicPath + "/" + url;
        }
        return url;
    }

    @Override
    public JSONObject rateLimitStatus() throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject verifyCredentials() throws ConnectionException {
        //return httpConnection.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return verifyCredentialsAttempt2();
    }

    /**
     * From Impeller code...
     * @return
     */
    private JSONObject verifyCredentialsAttempt2() {
        JSONObject activity;
        try {
            OAuthConsumer consumer = new DefaultOAuthConsumer(
                    httpConnection.connectionData.clientKeys.getConsumerKey(),
                    httpConnection.connectionData.clientKeys.getConsumerSecret());
            if (httpConnection.getCredentialsPresent(null)) {
                consumer.setTokenWithSecret(httpConnection.getUserToken(), httpConnection.getUserSecret());
            }
            
            URL url = new URL(httpConnection.pathToUrl(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS)));
            HttpURLConnection conn;
            loop: while(true) {
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                consumer.sign(conn);
                conn.connect();
                switch(conn.getResponseCode()) {
                    case 200:
                        break loop;  
                        
                    case 301:
                    case 302:
                    case 303:
                    case 307:
                        url = new URL(conn.getHeaderField("Location"));
                        MyLog.v(TAG, "Following redirect to " + url);
                        continue;
                        
                    default:
                        String err = readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                        throw new Exception(err);
                }
            }
            
            activity = new JSONObject(readAll(conn.getInputStream()));
        } catch(Exception e) {
            Log.e(TAG, "Error getting whoami", e);
            return null;
        }
        return activity;
    }
    
    @Override
    public JSONObject destroyFavorite(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject createFavorite(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject destroyStatus(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONArray getFriendsIds(String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject getStatus(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject updateStatus(String message, String inReplyToId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject postDirectMessage(String message, String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject postReblog(String rebloggedId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONArray getTimeline(ApiRoutineEnum apiRoutine, String sinceId, int limit, String userId)
            throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject followUser(String userId, Boolean follow) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject getUser(String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }
}
