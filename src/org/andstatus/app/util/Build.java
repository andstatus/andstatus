
package org.andstatus.app.util;

import java.lang.reflect.*;

/**
 * The class is for compatibility with v.1.5 of Android
 * 
 * @author yvolk
 */
public class Build {
    /** Various version strings. */
    public static class VERSION {

        /**
         * The user-visible SDK version of the framework; its possible values
         * are defined in {@link android.os.Build.VERSION_CODES}.
         */
        public static final int SDK_INT;
        static {
            int ver = 3; // v. 1.5
            try {
                Field verField = Class.forName("android.os.Build$VERSION").getField("SDK_INT");
                ver = verField.getInt(verField);
            } catch (Exception e) {
                try {
                    Field verField = Class.forName("android.os.Build$VERSION").getField("SDK");
                    String verString = (String) verField.get(verField);
                    ver = Integer.parseInt(verString);
                } catch (Exception e2) {
                    ver = -1;
                }
            }
            SDK_INT = ver;
        }
    }
}
