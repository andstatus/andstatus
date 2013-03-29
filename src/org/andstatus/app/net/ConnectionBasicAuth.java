/* 
 * Copyright (C) 2008 Torgny Bjers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.net;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.util.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;


/**
 * Handles connection to the Twitter REST API using Basic Authentication
 * 
 * @author torgny.bjers
 */
public class ConnectionBasicAuth extends Connection {

	private static final String USER_AGENT = "AndStatus/1.0";
	private static final String TAG = ConnectionBasicAuth.class.getSimpleName();

    /**
     * Creates a new ConnectionBasicAuth instance.
     */
    public ConnectionBasicAuth(MyAccount ma, ApiEnum api, String apiBaseUrl) {
        super(ma, api , apiBaseUrl);
    }

    public JSONArray getTimeline(String url, String sinceId, int limit, String userId) throws ConnectionException {
        url += "?count=" + Integer.toString(fixLimit(limit));
        if (!TextUtils.isEmpty(fixSinceId(sinceId))) {
            url += "&since_id=" + fixSinceId(sinceId);
        }
        if (!TextUtils.isEmpty(userId)) {
            url += "&user_id" + userId;
        }
        JSONArray jArr = null;
        String request = getRequest(url);
        try {
            jArr = new JSONArray(request);
        } catch (JSONException e) {
            try {
                JSONObject jObj = new JSONObject(request);
                String error = jObj.optString("error");
                if ("Could not authenticate you.".equals(error)) {
                    throw new ConnectionException(error);
                }
            } catch (JSONException e1) {
                throw new ConnectionException(e);
            }
        }
        return jArr;
    }
	
	@Override
    public JSONObject updateStatus(String message, String inReplyToId) throws ConnectionException {
		String url = getApiUrl(ApiRoutineEnum.STATUSES_UPDATE);
		List<NameValuePair> formParams = new ArrayList<NameValuePair>();
		formParams.add(new BasicNameValuePair("status", message));
		
		// This parameter was removed from API:
		// formParams.add(new BasicNameValuePair("source", SOURCE_PARAMETER));
		
		if ( !TextUtils.isEmpty(inReplyToId)) {
			formParams.add(new BasicNameValuePair("in_reply_to_status_id", inReplyToId));
		}
		JSONObject jObj = null;
		try {
			jObj = new JSONObject(postRequest(url, new UrlEncodedFormEntity(formParams, HTTP.UTF_8)));
			String error = jObj.optString("error");
			if ("Could not authenticate you.".equals(error)) {
				throw new ConnectionException(error);
			}
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
		} catch (JSONException e) {
			throw new ConnectionException(e);
		}
		return jObj;
	}

    @Override
    public JSONObject postDirectMessage(String message, String userId) throws ConnectionException {
        String url = getApiUrl(ApiRoutineEnum.POST_DIRECT_MESSAGE);
        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        formParams.add(new BasicNameValuePair("text", message));
        
        if ( !TextUtils.isEmpty(userId)) {
            formParams.add(new BasicNameValuePair("user_id", userId));
        }
        JSONObject jObj = null;
        try {
            jObj = new JSONObject(postRequest(url, new UrlEncodedFormEntity(formParams, HTTP.UTF_8)));
            String error = jObj.optString("error");
            if ("Could not authenticate you.".equals(error)) {
                throw new ConnectionException(error);
            }
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
        } catch (JSONException e) {
            throw new ConnectionException(e);
        }
        return jObj;
    }

    @Override
    public JSONObject postReblog(String rebloggedId) throws ConnectionException {
        JSONObject jObj = null;
        try {
            jObj = new JSONObject(postRequest(getApiUrl(ApiRoutineEnum.POST_REBLOG) + rebloggedId + EXTENSION));
            String error = jObj.optString("error");
            if ("Could not authenticate you.".equals(error)) {
                throw new ConnectionException(error);
            }
        } catch (JSONException e) {
            throw new ConnectionException(e);
        }
        return jObj;
    }

    @Override
    public JSONObject destroyStatus(String statusId) throws ConnectionException {
		JSONObject jso = null;
		try {
			jso = new JSONObject(postRequest(getApiUrl(ApiRoutineEnum.STATUSES_DESTROY) + statusId + EXTENSION));
			String error = jso.optString("error");
			if ("Could not authenticate you.".equals(error)) {
				throw new ConnectionException(error);
			}
		} catch (JSONException e) {
			throw new ConnectionException(e);
		}
		return jso;
	}

    @Override
    public JSONObject getStatus(String statusId) throws ConnectionException {
        JSONObject jso = null;
        try {
            Uri sUri = Uri.parse(getApiUrl(ApiRoutineEnum.STATUSES_SHOW));
            Uri.Builder builder = sUri.buildUpon();
            builder.appendQueryParameter("id", statusId);
            String response = getRequest(builder.build().toString());
            jso = new JSONObject(response);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConnectionException(e.getLocalizedMessage());
        }
        return jso;
    }
    
	@Override
    public JSONObject createFavorite(String statusId) throws ConnectionException {
		StringBuilder url = new StringBuilder(getApiUrl(ApiRoutineEnum.FAVORITES_CREATE_BASE));
		url.append(statusId);
		url.append(EXTENSION);
		JSONObject jObj = null;
		try {
			jObj = new JSONObject(postRequest(url.toString()));
			String error = jObj.optString("error");
			if ("Could not authenticate you.".equals(error)) {
				throw new ConnectionException(error);
			}
		} catch (JSONException e) {
			throw new ConnectionException(e);
		}
		return jObj;
	}

	@Override
    public JSONObject destroyFavorite(String statusId) throws ConnectionException {
		StringBuilder url = new StringBuilder(getApiUrl(ApiRoutineEnum.FAVORITES_DESTROY_BASE));
		url.append(statusId);
		url.append(EXTENSION);
		JSONObject jObj = null;
		try {
			jObj = new JSONObject(postRequest(url.toString()));
			String error = jObj.optString("error");
			if ("Could not authenticate you.".equals(error)) {
				throw new ConnectionException(error);
			}
		} catch (JSONException e) {
			throw new ConnectionException(e);
		}
		return jObj;
	}

	@Override
    public boolean getCredentialsPresent(MyAccount ma) {
        boolean yes = false;
        // This is not set for the new account
        mUsername = ma.getUsername();
        if (!TextUtils.isEmpty(mUsername) && !TextUtils.isEmpty(mPassword)) {
            yes = true;
        }
        return yes;
    }

    @Override
    public JSONObject verifyCredentials() throws ConnectionException {
        /**
         * Returns an HTTP 200 OK response code and a representation of the
         * requesting user if authentication was successful; returns a 401
         * status code and an error message if not.
         * 
         * @see <a
         *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials">Twitter
         *      REST API Method: account verify_credentials</a>
         */
        JSONObject jo = null;
        try {
            jo = new JSONObject(getRequest(getApiUrl(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS),
                    new DefaultHttpClient(new BasicHttpParams())));
        } catch (JSONException e) {
            Log.e(TAG, "verifyCredentials: " + e.toString());
        }

        return jo;
    }

	@Override
    public JSONObject rateLimitStatus() throws ConnectionException {
        JSONObject jo = null;
		try {
            jo = new JSONObject(getRequest(getApiUrl(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS), new DefaultHttpClient(new BasicHttpParams())));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jo;
	}

    @Override
    public void clearAuthInformation() {
        setPassword("");
    }
	
	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 */
	private String getRequest(String url) throws ConnectionException {
		return getRequest(url, new DefaultHttpClient(new BasicHttpParams()));
	}

	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @param client
	 * @return String
	 * @throws ConnectionException
	 */
	private String getRequest(String url, HttpClient client) throws ConnectionException {
		String result = null;
		int statusCode = 0;
		HttpGet getMethod = new HttpGet(url);
		try {
			getMethod.setHeader("User-Agent", USER_AGENT);
			getMethod.addHeader("Authorization", "Basic " + getCredentials());
			client.getParams().setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, DEFAULT_GET_REQUEST_TIMEOUT);
			client.getParams().setIntParameter(HttpConnectionParams.SO_TIMEOUT, DEFAULT_GET_REQUEST_TIMEOUT);
			HttpResponse httpResponse = client.execute(getMethod);
			statusCode = httpResponse.getStatusLine().getStatusCode();
			result = retrieveInputStream(httpResponse.getEntity());
		} catch (Exception e) {
            Log.e(TAG, "getRequest: " + e.toString());
			throw new ConnectionException(e);
		} finally {
			getMethod.abort();
		}
		parseStatusCode(statusCode, url);
		return result;
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 */
	private String postRequest(String url) throws ConnectionException {
		return postRequest(url, new DefaultHttpClient(new BasicHttpParams()), null);
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 */
	private String postRequest(String url, UrlEncodedFormEntity formParams) throws ConnectionException {
		return postRequest(url, new DefaultHttpClient(new BasicHttpParams()), formParams);
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @param client
	 * @return String
	 * @throws ConnectionException
	 */
	private String postRequest(String url, HttpClient client, UrlEncodedFormEntity formParams) throws ConnectionException {
		String result = null;
		int statusCode = 0;
		HttpPost postMethod = new HttpPost(url);
		try {
			postMethod.setHeader("User-Agent", USER_AGENT);
			postMethod.addHeader("Authorization", "Basic " + getCredentials());
			if (formParams != null) {
				postMethod.setEntity(formParams);
			}
			client.getParams().setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, DEFAULT_POST_REQUEST_TIMEOUT);
			client.getParams().setIntParameter(HttpConnectionParams.SO_TIMEOUT, DEFAULT_POST_REQUEST_TIMEOUT);
			HttpResponse httpResponse = client.execute(postMethod);
			statusCode = httpResponse.getStatusLine().getStatusCode();
			result = retrieveInputStream(httpResponse.getEntity());
		} catch (Exception e) {
            Log.e(TAG, "postRequest: " + e.toString());
			throw new ConnectionException(e);
		} finally {
			postMethod.abort();
		}
		parseStatusCode(statusCode, url);
		return result;
	}

	/**
	 * Retrieve the input stream from the HTTP connection.
	 * 
	 * @param httpEntity
	 * @return String
	 */
	private String retrieveInputStream(HttpEntity httpEntity) {
		int length = (int) httpEntity.getContentLength();
		if ( length <= 0 ) {
		    // Length is unknown or large
		    length = 1024;
		}
		StringBuffer stringBuffer = new StringBuffer(length);
		try {
			InputStreamReader inputStreamReader = new InputStreamReader(httpEntity.getContent(), HTTP.UTF_8);
			char buffer[] = new char[length];
			int count;
			while ((count = inputStreamReader.read(buffer, 0, length - 1)) > 0) {
				stringBuffer.append(buffer, 0, count);
			}
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, e.toString());
		} catch (IllegalStateException e) {
			Log.e(TAG, e.toString());
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
		return stringBuffer.toString();
	}

	/**
	 * Get the HTTP digest authentication. Uses Base64 to encode credentials.
	 * 
	 * @return String
	 */
	private String getCredentials() {
		return new String(Base64.encodeBytes((mUsername + ":" + mPassword).getBytes()));
	}

	/**
	 * Parse the status code and throw appropriate exceptions when necessary.
	 * 
	 * @param code
	 * @param path
	 * @throws ConnectionException
	 */
	private void parseStatusCode(int code, String path) throws ConnectionException {
		switch (code) {
		case 200:
		case 304:
			break;
		case 401:
			throw new ConnectionException(String.valueOf(code));
		case 400:
		case 403:
		case 404:
			throw new ConnectionException(String.valueOf(code));
		case 500:
		case 502:
		case 503:
			throw new ConnectionException(String.valueOf(code));
		}
	}

    @Override
    public boolean isPasswordNeeded() {
        return true;
    }

    @Override
    public boolean isOAuth() {
        return false;
    }
}
