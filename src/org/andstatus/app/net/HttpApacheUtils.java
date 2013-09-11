package org.andstatus.app.net;

import android.text.TextUtils;
import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class HttpApacheUtils {
    private static final String TAG = HttpApacheUtils.class.getSimpleName();
    
    private HttpApacheRequest request;
    
    HttpApacheUtils(HttpApacheRequest request) {
        this.request = request;
    }

    final JSONArray getRequestAsArray(HttpGet get) throws ConnectionException {
        JSONArray jsa = null;
        JSONTokener jst = request.getRequest(get);
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
    
    final JSONObject getRequestAsObject(HttpGet get) throws ConnectionException {
        JSONObject jso = null;
        JSONTokener jst = request.getRequest(get);
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

    protected JSONObject postRequest(String path) throws ConnectionException {
        HttpPost post = new HttpPost(request.pathToUrl(path));
        return request.postRequest(post);
    }
    
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        List<NameValuePair> formParams = HttpApacheUtils.jsonToNameValuePair(jso);
        HttpPost postMethod = new HttpPost(request.pathToUrl(path));
        try {
            if (formParams != null) {
                UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(formParams, HTTP.UTF_8);
                postMethod.setEntity(formEntity);
            }
            jso = request.postRequest(postMethod);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
        }
        return jso;
    }
    
    /**
     * @throws ConnectionException
     */
    final static List<NameValuePair> jsonToNameValuePair(JSONObject jso) throws ConnectionException {
        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        for (@SuppressWarnings("unchecked")
        Iterator<String> iterator = jso.keys(); iterator.hasNext();) {
            String name = iterator.next();
            String value = jso.optString(name);
            if (!TextUtils.isEmpty(value)) {
                formParams.add(new BasicNameValuePair(name, value));
            }
        }
        return formParams;
    }
}
