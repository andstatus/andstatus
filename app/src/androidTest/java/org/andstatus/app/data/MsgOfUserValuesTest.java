package org.andstatus.app.data;

import android.content.ContentValues;
import android.test.InstrumentationTestCase;

import org.andstatus.app.database.DatabaseHolder.MsgOfUser;

public class MsgOfUserValuesTest extends InstrumentationTestCase {
    public void testCreationFromContentValues() {
       ContentValues contentValues = new ContentValues();
       long userId = 0;
       long msgId = 2;
       MsgOfUserValues userValues = MsgOfUserValues.valueOf(userId , contentValues);
       assertFalse(userValues.isValid());

       userId = 1;
       userValues = MsgOfUserValues.valueOf(userId , contentValues);
       assertFalse(userValues.isValid());
       userValues.setMsgId(msgId);
       assertTrue(userValues.isValid());
       assertTrue(userValues.isEmpty());
       
       contentValues.put(MsgOfUser.SUBSCRIBED, true);
       userValues = MsgOfUserValues.valueOf(userId , contentValues);
       assertFalse(userValues.isValid());
       userValues.setMsgId(msgId);
       assertTrue(userValues.isValid());
       assertFalse(userValues.isEmpty());
    }
}
