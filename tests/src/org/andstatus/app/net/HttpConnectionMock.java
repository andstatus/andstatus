package org.andstatus.app.net;

import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HttpConnectionMock extends HttpConnection {
    private static final String TAG = HttpConnectionMock.class.getSimpleName();
    private JSONObject postedObject = null;
    private JSONObject responseObject = null;

    public void setResponse(JSONObject jso) {
        responseObject = jso;
    }
    
    @Override
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        postedObject = jso;
        return responseObject;
    }

    @Override
    protected JSONObject postRequest(String path) throws ConnectionException {
        return responseObject;
    }

    @Override
    protected JSONObject getRequest(String path) throws ConnectionException {
        return responseObject;
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

    @Override
    public void clearAuthInformation() {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean getCredentialsPresent() {
        // TODO Auto-generated method stub
        return false;
    }

    public JSONObject getPostedJSONObject() {
        return postedObject;
    }

}
