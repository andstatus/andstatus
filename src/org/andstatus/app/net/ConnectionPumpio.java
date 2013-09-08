package org.andstatus.app.net;

import android.net.Uri;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;

import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of pump.io API: <a href="https://github.com/e14n/pump.io/blob/master/API.md">https://github.com/e14n/pump.io/blob/master/API.md</a>  
 * @author yvolk
 */
class ConnectionPumpio extends Connection {
    private enum PumpioObjectType {
        ACTIVITY("activity") {
            @Override
            public boolean isMyType(JSONObject jso) {
                boolean is = false;
                if (jso != null) {
                     is = jso.has("verb");
                     // It may not have the "objectType" field as in the specification:
                     //   http://activitystrea.ms/specs/json/1.0/
                }
                return is;
            }
        },
        PERSON("person"),
        COMMENT("comment"),
        NOTE("note");
        
        private String fieldName;
        PumpioObjectType(String fieldName) {
            this.fieldName = fieldName;
        }
        
        public String fieldName() {
            return fieldName;
        }
        
        public boolean isMyType(JSONObject jso) {
            boolean is = false;
            if (jso != null) {
                is = fieldName().equalsIgnoreCase(jso.optString("objectType"));
            }
            return is;
        }
        
    }

    private static final String TAG = ConnectionPumpio.class.getSimpleName();
    public ConnectionPumpio(OriginConnectionData connectionData) {
        super(connectionData);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void registerClient() {
        registerClientAttempt2();
        if (httpConnection.connectionData.clientKeys.areKeysPresent()) {
            MyLog.v(TAG, "Registered client for " + httpConnection.connectionData.host);
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
            params.put("redirect_uris", Origin.CALLBACK_URI.toString());
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
        } catch (ConnectionException e) {
            Log.e(TAG, "registerClient Exception: " + e.toString());
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
            case STATUSES_HOME_TIMELINE:
                url = "user/%nickname%/inbox";
                break;
            case STATUSES_UPDATE:
                url = "user/%nickname%/feed";
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
    public MbRateLimitStatus rateLimitStatus() throws ConnectionException {
        MbRateLimitStatus status = new MbRateLimitStatus();
        // TODO Auto-generated method stub
        return status;
    }

    @Override
    public MbUser verifyCredentials() throws ConnectionException {
        // TODO: This gives "Bad Request" error
        // JSONObject user = httpConnection.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        JSONObject user = getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return userFromJson(user);
    }

    private MbUser userFromJson(JSONObject jso) throws ConnectionException {
        if (!PumpioObjectType.PERSON.isMyType(jso)) {
            return MbUser.getEmpty();
        }
        String oid = jso.optString("id");
        MbUser user = MbUser.fromOriginAndUserOid(httpConnection.connectionData.originId, oid);
        user.reader = MbUser.fromOriginAndUserOid(httpConnection.connectionData.originId, accountUserOid);
        user.userName = userOidToUsername(oid);
        user.oid = oid;
        user.realName = jso.optString("displayName");
        if (jso.has("image")) {
            JSONObject image = jso.optJSONObject("image");
            if (image != null) {
                user.avatarUrl = image.optString("url");
            }
        }
        user.description = jso.optString("summary");
        user.homepage = jso.optString("url");
        user.updatedDate = dateFromJson(jso, "updated");
        return user;
    }
    
    private long dateFromJson(JSONObject jso, String fieldName) {
        long date = 0;
        if (jso != null && jso.has(fieldName)) {
            String updated = jso.optString(fieldName);
            if (updated.length() > 0) {
                date = parseDate(updated);
            }
        }
        return date;
    }
    
    /**
     * Simple solution based on:
     * http://stackoverflow.com/questions/2201925/converting-iso8601-compliant-string-to-java-util-date
     */
    private static long parseDate(String date) {
        if(date == null)
            return new Date().getTime();
        String datePrepared;        
        if (date.lastIndexOf("Z") == date.length()-1) {
            datePrepared = date.substring(0, date.length()-1) + "+0000";
        } else {
            datePrepared = date.replaceAll("\\+0([0-9]){1}\\:00", "+0$100");
        }
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.GERMANY);
        try {
            long unixTime = df.parse(datePrepared).getTime();
            return unixTime;
        } catch (ParseException e) {
            Log.e(TAG, "Failed to parse the date: '" + date +"'");
            return new Date().getTime();
        }
    }
    
    // TODO: Move to HttpConnection...
    JSONObject getRequest(String path) throws ConnectionException {
        if (TextUtils.isEmpty(path)) {
            throw new IllegalArgumentException("path is empty");
        }
        JSONObject result = null;
        try {
            OAuthConsumer consumer = getConsumer();
            
            URL url = new URL(httpConnection.pathToUrl(path));
            HttpURLConnection conn;
            for (boolean done=false; !done; ) {
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                consumer.sign(conn);
                conn.connect();
                int responseCode = conn.getResponseCode();
                switch(responseCode) {
                    case 200:
                        result = new JSONObject(readAll(conn.getInputStream()));
                        done = true;
                        break;
                    case 301:
                    case 302:
                    case 303:
                    case 307:
                        url = new URL(conn.getHeaderField("Location"));
                        MyLog.v(TAG, "Following redirect to " + url);
                        break;                        
                    default:
                        String responseString = readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                        try {
                            JSONObject jsonError = new JSONObject(responseString);
                            String error = jsonError.optString("error");
                            StatusCode statusCode = (error.indexOf("not found") < 0 ? StatusCode.UNKNOWN : StatusCode.NOT_FOUND);
                            throw new ConnectionException(statusCode, "Error getting '" + path + "', status=" + responseCode + ", error='" + error + "'");
                        } catch (JSONException e) {
                            throw new ConnectionException("Error getting '" + path + "', status=" + responseCode + ", non-JSON response: '" + responseString + "'");
                        }
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

    private OAuthConsumer getConsumer() {
        OAuthConsumer consumer = new DefaultOAuthConsumer(
                httpConnection.connectionData.clientKeys.getConsumerKey(),
                httpConnection.connectionData.clientKeys.getConsumerSecret());
        if (httpConnection.getCredentialsPresent()) {
            consumer.setTokenWithSecret(httpConnection.getUserToken(), httpConnection.getUserSecret());
        }
        return consumer;
    }
    
    /**
     * @return not null
     */
    private JSONArray getRequestAsArray(String path) throws ConnectionException {
        JSONObject jso = getRequest(path);
        JSONArray jsa = null;
        if (jso == null) {
            // TODO: ensure not null
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
    public MbMessage destroyFavorite(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return MbMessage.getEmpty();
    }

    @Override
    public MbMessage createFavorite(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return MbMessage.getEmpty();
    }

    @Override
    public boolean destroyStatus(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<String> getIdsOfUsersFollowedBy(String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return new ArrayList<String>();
    }

    @Override
    public MbMessage getMessage(String messageId) throws ConnectionException {
        // TODO Auto-generated method stub
        return MbMessage.getEmpty();
    }

    @Override
    public MbMessage updateStatus(String message, String inReplyToId) throws ConnectionException {
        JSONObject activity = new JSONObject();
        try {
            activity.put("objectType", "activity");
            activity.put("verb", "post");

            JSONObject generator = new JSONObject();
            generator.put("id", APPLICATION_ID);
            generator.put("displayName", HttpConnection.USER_AGENT);
            generator.put("objectType", "application");
            activity.put("generator", generator);
            
            JSONObject thePublic = new JSONObject();
            thePublic.put("id", "http://activityschema.org/collection/public");
            thePublic.put("objectType", "collection");
            JSONArray to = new JSONArray();
            to.put(thePublic);
            activity.put("to", to);
            
            JSONObject comment = new JSONObject();
            comment.put("objectType", "comment");
            comment.put("content", message);
            if (!TextUtils.isEmpty(inReplyToId)) {
                JSONObject inReplyToObject = new JSONObject();
                inReplyToObject.put("id", inReplyToId);
                inReplyToObject.put("objectType", oidToObjectType(inReplyToId));
                comment.put("inReplyTo", inReplyToObject);
            }
            
            JSONObject author = new JSONObject();
            author.put("objectType", "person");
            author.put("id", accountUserOid);
            comment.put("author", author);

            activity.put("object", comment);
            activity.put("actor", author);
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, activity, "Error posting message '" + message + "'");
        }
        
        JSONObject jso = postJSON(getApiPathForThisAccount(ApiRoutineEnum.STATUSES_UPDATE), activity);
        return messageFromJson(jso);
    }
    
    String oidToObjectType(String oid) {
        String objectType = "";
        if (oid.contains("/comment/")) {
            objectType = "comment";
        } else if (oid.contains("/note/")) {
            objectType = "note";
        } else if (oid.contains("/notice/")) {
            objectType = "note";
        } else if (oid.contains("/person/")) {
            objectType = "person";
        } else if (oid.contains("/user/")) {
            objectType = "person";
        } else {
            String pattern = "/api/";
            int indStart = oid.indexOf(pattern);
            if (indStart >= 0) {
                int indEnd = oid.indexOf("/", indStart+pattern.length());
                if (indEnd > indStart) {
                    objectType = oid.substring(indStart+pattern.length(), indEnd);
                }
            }
        }
        if (TextUtils.isEmpty(objectType)) {
            objectType = "unknown object type: " + oid;
            Log.e(TAG, objectType);
        }
        return objectType;
    }
    
    private String getApiPathForThisAccount(ApiRoutineEnum apiRoutine) throws ConnectionException {
        String url = this.getApiPath(apiRoutine);
        url = url.replace("%nickname%",  userOidToNickname(accountUserOid));
        return url;
    }

    private JSONObject postJSON(String path, JSONObject activity) throws ConnectionException {
        JSONObject result = null;
        try {
            MyLog.v(TAG, "Posting " + activity.toString(2));
            OAuthConsumer cons = getConsumer();
        
            URL url = new URL(httpConnection.pathToUrl(path));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            cons.sign(conn);
            
            OutputStream os = conn.getOutputStream();
            OutputStreamWriter wr = new OutputStreamWriter(os);
            String toWrite = activity.toString(); 
            wr.write(toWrite);
            wr.close();
                        
            int responseCode = conn.getResponseCode();
            switch(responseCode) {
                case 200:
                    result = new JSONObject(readAll(conn.getInputStream()));
                    break;
                default:
                    String responseString = readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
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
    
    @Override
    public MbMessage postDirectMessage(String message, String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return MbMessage.getEmpty();
    }

    @Override
    public MbMessage postReblog(String rebloggedId) throws ConnectionException {
        // TODO Auto-generated method stub
        return MbMessage.getEmpty();
    }

    @Override
    public List<MbMessage> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition sinceId, int limit, String userId)
            throws ConnectionException {
        String url = this.getApiPath(apiRoutine);
        if (TextUtils.isEmpty(url)) {
            return new ArrayList<MbMessage>();
        }
        if (TextUtils.isEmpty(userId)) {
            throw new IllegalArgumentException("getTimeline: userId is required");
        }
        String nickname = userOidToNickname(userId);
        if (TextUtils.isEmpty(nickname)) {
            throw new IllegalArgumentException("getTimeline: wrong userId=" + userId);
        }
        url = url.replace("%nickname%", nickname);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (!sinceId.isEmpty()) {
            // The "since" should point to the "Activity" on the timeline, not to the message
            // Otherwise we will always get "not found"
            builder.appendQueryParameter("since", sinceId.getPosition());
        }
        if (fixedDownloadLimit(limit) > 0) {
            builder.appendQueryParameter("count",String.valueOf(fixedDownloadLimit(limit)));
        }
        url = builder.build().toString();
        JSONArray jArr = getRequestAsArray(url);
        List<MbMessage> timeline = new ArrayList<MbMessage>();
        if (jArr != null) {
            // Read the activities in chronological order
            for (int index = jArr.length() - 1; index >= 0; index--) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    MbMessage mbMessage = messageFromJson(jso);
                    timeline.add(mbMessage);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(TAG, e, null, "Parsing timeline");
                }
            }
        }
        MyLog.d(TAG, "getTimeline '" + url + "' " + timeline.size() + " messages");
        return timeline;
    }

    @Override
    public int fixedDownloadLimit(int limit) {
        final int maxLimit = 20;
        int out = super.fixedDownloadLimit(limit);
        if (out > maxLimit) {
            out = maxLimit;
        }
        return out;
    }
    
    private MbMessage messageFromJson(JSONObject jso) throws ConnectionException {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            try {
                MyLog.v(TAG, "messageFromJson: " + jso.toString(2));
            } catch (JSONException e) {
                ConnectionException.loggedJsonException(TAG, e, jso, "messageFromJson");
            }
        }
        if (PumpioObjectType.ACTIVITY.isMyType(jso)) {
            return messageFromJsonActivity(jso);
        } else if (PumpioObjectType.COMMENT.isMyType(jso) || PumpioObjectType.NOTE.isMyType(jso)) {
            return messageFromJsonComment(jso);
        } else {
            return MbMessage.getEmpty();
        }
    }
    
    private MbMessage messageFromJsonActivity(JSONObject activity) throws ConnectionException {
        MbMessage message;
        try {
            String verb = activity.getString("verb");
            String oid = activity.optString("id");
            if (TextUtils.isEmpty(oid)) {
                MyLog.d(TAG, "Pumpio activity has no id:" + activity.toString(2));
                return MbMessage.getEmpty();
            } 
            message =  MbMessage.fromOriginAndOid(httpConnection.connectionData.originId, oid);
            message.reader = MbUser.fromOriginAndUserOid(httpConnection.connectionData.originId, accountUserOid);
            message.sentDate = dateFromJson(activity, "updated");
            message.timelineItemDate = message.sentDate; 

            if (activity.has("actor")) {
                message.sender = userFromJson(activity.getJSONObject("actor"));
            }
            if (activity.has("to")) {
                JSONObject to = activity.optJSONObject("to");
                if ( to != null) {
                    message.recipient = userFromJson(to);
                } else {
                    JSONArray arrayOfTo = activity.optJSONArray("to");
                    if (arrayOfTo != null && arrayOfTo.length() == 1) {
                        // TODO: handle multiple recipients
                        to = arrayOfTo.optJSONObject(0);
                        message.recipient = userFromJson(to);
                    }
                }
            }
            if (activity.has("generator")) {
                JSONObject generator = activity.getJSONObject("generator");
                if (generator.has("displayName")) {
                    message.via = generator.getString("displayName");
                }
            }
            
            JSONObject jso = activity.getJSONObject("object");
            // Is this a reblog ("Share" in terms of Activity streams)?
            if (verb.equalsIgnoreCase("share")) {
                message.rebloggedMessage = messageFromJson(jso);
                if (message.rebloggedMessage.isEmpty()) {
                    MyLog.d(TAG, "No reblogged message " + jso.toString(2));
                    return message.markAsEmpty();
                }
            } else {
                if (verb.equalsIgnoreCase("favorite")) {
                    message.favoritedByReader = true;
                } else if (verb.equalsIgnoreCase("unfavorite") || verb.equalsIgnoreCase("unlike")) {
                    message.favoritedByReader = false;
                }
                
                if (PumpioObjectType.COMMENT.isMyType(jso) || PumpioObjectType.NOTE.isMyType(jso)) {
                    parseComment(message, jso);
                } else {
                    return message.markAsEmpty();
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, activity, "Parsing activity");
        }
        return message;
    }

    private void parseComment(MbMessage message, JSONObject jso) throws ConnectionException {
        try {
            String oid = jso.optString("id");
            if (!TextUtils.isEmpty(oid)) {
                if (!message.oid.equalsIgnoreCase(oid)) {
                    message.oid = oid;
                }
            } 
            if (jso.has("author")) {
                MbUser author = userFromJson(jso.getJSONObject("author"));
                if (!author.isEmpty()) {
                    message.sender = author;
                }
            }
            if (jso.has("content")) {
                message.body = Html.fromHtml(jso.getString("content")).toString();
            }
            message.sentDate = dateFromJson(jso, "published");

            if (jso.has("generator")) {
                JSONObject generator = jso.getJSONObject("generator");
                if (generator.has("displayName")) {
                    message.via = generator.getString("displayName");
                }
            }

            // If the Msg is a Reply to other message
            if (jso.has("inReplyTo")) {
                JSONObject inReplyToObject = jso.getJSONObject("inReplyTo");
                message.inReplyToMessage = messageFromJson(inReplyToObject);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, jso, "Parsing comment/note");
        }
    }
    
    private MbMessage messageFromJsonComment(JSONObject jso) throws ConnectionException {
        MbMessage message;
        try {
            String oid = jso.optString("id");
            if (TextUtils.isEmpty(oid)) {
                MyLog.d(TAG, "Pumpio object has no id:" + jso.toString(2));
                return MbMessage.getEmpty();
            } 
            message =  MbMessage.fromOriginAndOid(httpConnection.connectionData.originId, oid);
            message.reader = MbUser.fromOriginAndUserOid(httpConnection.connectionData.originId, accountUserOid);

            parseComment(message, jso);
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, jso, "Parsing comment");
        }
        return message;
    }
    
    private String userOidToUsername(String userId) {
        String username = "";
        if (!TextUtils.isEmpty(userId)) {
            int indexOfColon = userId.indexOf(":");
            if (indexOfColon > 0) {
                username = userId.substring(indexOfColon+1);
            }
        }
        return username;
    }
    
    private String userOidToNickname(String userId) {
        String nickname = "";
        if (!TextUtils.isEmpty(userId)) {
            int indexOfColon = userId.indexOf(":");
            int indexOfAt = userId.indexOf("@");
            if (indexOfColon > 0 && indexOfAt > indexOfColon) {
                nickname = userId.substring(indexOfColon+1, indexOfAt);
            }
        }
        return nickname;
    }
    
    @Override
    public MbUser followUser(String userId, Boolean follow) throws ConnectionException {
        // TODO Auto-generated method stub
        return MbUser.getEmpty();
    }

    @Override
    public MbUser getUser(String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return MbUser.getEmpty();
    }
}
