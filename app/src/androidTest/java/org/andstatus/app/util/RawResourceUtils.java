/*
 * Based on the example: 
 * http://stackoverflow.com/questions/4087674/android-read-text-raw-resource-file
 */
package org.andstatus.app.util;

import android.content.Context;
import android.content.res.Resources;
import androidx.annotation.RawRes;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RawResourceUtils {

    public static String getString(@RawRes int id) throws IOException {
        return new String(getBytes(id, InstrumentationRegistry.getInstrumentation().getContext()),
                StandardCharsets.UTF_8);
    }

    /**
     *  reads resources regardless of their size
     */
    public static byte[] getBytes(@RawRes int id, Context context) throws IOException {
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
