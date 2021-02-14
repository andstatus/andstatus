/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.database;

import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DatabaseCreatorTest {

    @Before
    public void setUp() {
        TestSuite.initialize(this);
    }

    @Test
    public void testTablesCreated() {
        MyLog.v(this, "Starting testTablesCreated");
        final SQLiteDatabase database = myContextHolder.getNow().getDatabase();
        assertEquals(true, database.isOpen());
        long originId = MyQuery.conditionToLongColumnValue(database, "", OriginTable.TABLE_NAME,
                OriginTable._ID, OriginTable.ORIGIN_NAME + "='Twitter'");
        assertNotEquals("Origin Twitter doesn't exist", 0, originId );
    }

}
