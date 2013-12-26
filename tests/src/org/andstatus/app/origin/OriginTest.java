package org.andstatus.app.origin;

import android.test.InstrumentationTestCase;

import org.andstatus.app.MyContextHolder;
import org.andstatus.app.TestSuite;
import org.andstatus.app.net.MbConfig;
import org.andstatus.app.origin.Origin.Builder;

public class OriginTest  extends InstrumentationTestCase {

        @Override
        protected void setUp() throws Exception {
            super.setUp();
            TestSuite.initialize(this);
        }

        public void testTextLimit() {
            String message = "I set \"Shorten URL with: QTTR.AT\" URL longer than 25 Text longer than 140. Will this be shortened: https://github.com/andstatus/andstatus/issues/41";

            Origin origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.TWITTER);
            int textLimit = 140;
            assertEquals("Textlimit", textLimit, origin.textLimit);
            assertEquals("Short URL length", 23, origin.shortUrlLength);
            assertEquals("Characters left", 18, origin.charactersLeftForMessage(message));

            origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.PUMPIO);
            textLimit = 5000;
            assertEquals("Textlimit", textLimit, origin.textLimit);
            assertEquals("Short URL length", 0, origin.shortUrlLength);
            assertEquals("Characters left", origin.textLimit - message.length(), origin.charactersLeftForMessage(message));
            
            origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.STATUSNET);
            textLimit = 200;
            MbConfig config = MbConfig.fromTextLimit(textLimit);
            origin = new Origin.Builder(origin).save(config).build();
            assertEquals("Textlimit", textLimit, origin.textLimit);
            
            textLimit = 140;
            config = MbConfig.fromTextLimit(textLimit);
            origin = new Origin.Builder(origin).save(config).build();
            assertEquals("Textlimit", textLimit, origin.textLimit);
            assertEquals("Short URL length", 0, origin.shortUrlLength);
            assertEquals("Characters left", textLimit - message.length(), origin.charactersLeftForMessage(message));
         }
        
        public void testAddDeleteOrigin() {
            String seed = Long.toString(System.nanoTime());
            String originName = "snTest" + seed;
            OriginType originType = OriginType.STATUSNET;
            String host = "sn" + seed + ".example.org";
            boolean isSsl = true;
            boolean allowHtml = true;
            createOneOrigin(originType, originName, host, isSsl, allowHtml);
            isSsl = false;
            Origin.Builder builder = createOneOrigin(originType, originName, host, isSsl, allowHtml);
            Origin origin = builder.build();
            assertEquals("New origin has no children", false, origin.hasChildren());
            assertEquals("Origin deleted", true, builder.delete());
        }
        
        public static Builder createOneOrigin(OriginType originType, String originName, String host,
                boolean isSsl, boolean allowHtml) {
            Origin.Builder builder = new Origin.Builder(originType);
            builder.setName(originName);
            builder.setHost(host);
            builder.setSsl(isSsl);
            builder.setHtmlContentAllowed(allowHtml);
            builder.save();
            Origin origin = builder.build();
            checkAttributes(originName, host, isSsl, allowHtml, origin);
            
            MyContextHolder.get().persistentOrigins().initialize();
            Origin origin2 = MyContextHolder.get().persistentOrigins().fromId(origin.getId());
            checkAttributes(originName, host, isSsl, allowHtml, origin2);
            
            return builder;
        }

        private static void checkAttributes(String originName, String host, boolean isSsl,
                boolean allowHtml, Origin origin) {
            assertTrue("Origin " + originName + " added", origin.isPersistent());
            assertEquals(originName, origin.getName());
            if (origin.canSetHostOfOrigin()) {
                assertEquals(host, origin.getHost());
            } else {
                assertEquals(origin.getOriginType().hostDefault, origin.getHost());
            }
            assertEquals(isSsl, origin.isSsl());
            assertEquals(allowHtml, origin.isHtmlContentAllowed());
        }
}
