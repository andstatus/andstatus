/*
 * Based on the example: 
 * http://stackoverflow.com/questions/4087674/android-read-text-raw-resource-file
 */
package org.andstatus.app.util;

import android.content.Context;
import android.content.res.Resources;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RawResourceUtils {
    
    public static JSONObject getJSONObject(Context context, int id) {
        JSONObject jso = null;
        try {
            jso = new JSONObject(RawResourceUtils.getString(context, id));
        } catch (JSONException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e.getMessage());
        }
        return jso;
    }

    public static String getString(Context context, int id) throws IOException {
        // TODO: reads an UTF-8 string resource - API 9 required
        //return new String(getResource(id, context), Charset.forName("UTF-8"));
        return new String(getBytes(id, context));
    }

    /* reads resources regardless of their size
     */
    public static byte[] getBytes(int id, Context context) throws IOException {
        Resources resources = context.getResources();
        InputStream is = resources.openRawResource(id);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        byte[] readBuffer = new byte[4 * 1024];

        try {
            int read;
            do {
                read = is.read(readBuffer, 0, readBuffer.length);
                if(read == -1) {
                    break;
                }
                bout.write(readBuffer, 0, read);
            } while(true);

            return bout.toByteArray();
        } finally {
            is.close();
        }
    }
}
