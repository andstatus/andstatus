/*
 * Based on the example: 
 * http://stackoverflow.com/questions/4087674/android-read-text-raw-resource-file
 */
package org.andstatus.app.net;

import android.content.Context;
import android.content.res.Resources;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RawResourceReader {
    /* reads resources regardless of their size
     */
    public static byte[] getResource(int id, Context context) throws IOException {
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

    public static String getStringResource(Context context, int id) throws IOException {
        // reads an UTF-8 string resource - API 9 required
        //return new String(getResource(id, context), Charset.forName("UTF-8"));
        return new String(getResource(id, context));
    }
    
    public static JSONObject getJSONObjectResource(Context context, int id) {
        JSONObject jso = null;
        try {
            jso = new JSONObject(RawResourceReader.getStringResource(context, id));
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return jso;
    }
}
