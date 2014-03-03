package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;

public class DataPrunerTest extends InstrumentationTestCase  {
    public void testPruneSqlSyntax() {
        DataPruner dp = new DataPruner(MyContextHolder.get().context());
        dp.prune();
    }
}
