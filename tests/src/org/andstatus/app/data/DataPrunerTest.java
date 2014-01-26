package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

import org.andstatus.app.MyContextHolder;

public class DataPrunerTest extends InstrumentationTestCase  {
    public void testPruneSqlSyntax() {
        DataPruner dp = new DataPruner(MyContextHolder.get().context());
        dp.prune();
    }
}
