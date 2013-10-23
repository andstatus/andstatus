package org.andstatus.app.origin;

import android.test.InstrumentationTestCase;

import org.andstatus.app.TestSuite;
import org.andstatus.app.net.MbConfig;
import org.andstatus.app.origin.Origin.OriginEnum;

public class OriginTest  extends InstrumentationTestCase {

        @Override
        protected void setUp() throws Exception {
            super.setUp();
            TestSuite.initialize(this);
        }

        public void testNewOrigin() {
            String message = "I set \"Shorten URL with: QTTR.AT\" URL longer than 25 Text longer than 140. Will this be shortened: https://github.com/andstatus/andstatus/issues/41";

            Origin origin = OriginEnum.TWITTER.newOrigin();
            int textLimit = 140;
            assertEquals("Textlimit", textLimit, origin.textLimit);
            assertEquals("Short URL length", 23, origin.shortUrlLength);
            assertEquals("Characters left", 18, origin.charactersLeftForMessage(message));

            origin = OriginEnum.PUMPIO.newOrigin();
            textLimit = 5000;
            assertEquals("Textlimit", textLimit, origin.textLimit);
            assertEquals("Short URL length", 0, origin.shortUrlLength);
            assertEquals("Characters left", origin.textLimit - message.length(), origin.charactersLeftForMessage(message));
            
            origin = OriginEnum.STATUSNET.newOrigin();
            textLimit = 200;
            MbConfig config = MbConfig.fromTextLimit(textLimit);
            origin.save(config);
            assertEquals("Textlimit", textLimit, origin.textLimit);
            
            textLimit = 140;
            config = MbConfig.fromTextLimit(textLimit);
            origin.save(config);
            assertEquals("Textlimit", textLimit, origin.textLimit);
            assertEquals("Short URL length", 0, origin.shortUrlLength);
            assertEquals("Characters left", textLimit - message.length(), origin.charactersLeftForMessage(message));
         }
}
