/*
 * Copyright (C) 2010 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app;

import android.content.Context;
import android.test.ActivityTestCase;
import android.util.Log;

import org.andstatus.app.util.*;

public class MyUtilTest extends ActivityTestCase {
    static final String TAG = MyUtilTest.class.getSimpleName();
    
    /**
     * Test I18n class...
     * @author yvolk (Yuri Volkov), http://yurivolkov.com
     */
    public void test001MessageFormat() throws Exception {
    	Log.i(TAG,"testMessageFormat started");
    	Context context = getInstrumentation().getTargetContext();
    	
        int len = messageFormatTests.length;
        for (int index = 0; index < len; index++) {
            MessageFormatTest messageFormatTest = messageFormatTests[index];
            if (messageFormatTest == null) { 
            	break; 
            }
            int messageFormat = messageFormatTest.messageFormat;
            int numSomething = messageFormatTest.numSomething;
            int array_patterns = messageFormatTest.array_patterns;
            int array_formats = messageFormatTest.array_formats;
            String output = I18n.formatQuantityMessage(context, messageFormat, numSomething, array_patterns, array_formats);
            /*
            if (!dateTest.expectedOutput.equals(output)) {
                Log.i("FormatDateRangeTest", "index " + index
                        + " expected: " + dateTest.expectedOutput
                        + " actual: " + output);
            } */
        	Log.i(TAG,"num=" + numSomething + "; output=\"" + output + "\"");
            
            //assertEquals(dateTest.expectedOutput, output);
        }         
    	Log.i(TAG,"testMessageFormat ended");
    }   

    static private class MessageFormatTest {
    	public int messageFormat = 0;
    	public int numSomething = 0;
    	public int array_patterns = 0;
    	public int array_formats = 0;
        // Is not used yet...
        public String expectedOutput;
        
        public MessageFormatTest(int messageFormat,
    			int numSomething, int array_patterns, int array_formats) {
        	this.messageFormat = messageFormat;
        	this.numSomething = numSomething;
        	this.array_patterns = array_patterns;
        	this.array_formats = array_formats;
        }
    }
    
    MessageFormatTest[] messageFormatTests = new MessageFormatTest[]{ 
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			1, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			2, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
				3, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			4, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			5, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			11, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			12, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			21, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			52, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			111, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			114, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			123, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			211, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
    	new MessageFormatTest(R.string.appwidget_new_tweet_format,
    			2123, R.array.appwidget_message_patterns, R.array.appwidget_message_formats),	
        new MessageFormatTest(R.string.appwidget_new_mention_format,
                1, R.array.appwidget_mention_patterns, R.array.appwidget_mention_formats),
        new MessageFormatTest(R.string.appwidget_new_mention_format,
                2, R.array.appwidget_mention_patterns, R.array.appwidget_mention_formats),
        new MessageFormatTest(R.string.appwidget_new_mention_format,
                5, R.array.appwidget_mention_patterns, R.array.appwidget_mention_formats),
        new MessageFormatTest(R.string.appwidget_new_message_format,
                1, R.array.appwidget_directmessage_patterns, R.array.appwidget_directmessage_formats),
        new MessageFormatTest(R.string.appwidget_new_message_format,
                2, R.array.appwidget_directmessage_patterns, R.array.appwidget_directmessage_formats),
        new MessageFormatTest(R.string.appwidget_new_message_format,
                5, R.array.appwidget_directmessage_patterns, R.array.appwidget_directmessage_formats)
    };
 	
}
