package org.andstatus.app.context;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class MyFutureTaskExpirable<T> extends FutureTask<T> {

    public MyFutureTaskExpirable(Callable<T> callable) {
        super(callable);
    }

    private volatile boolean isExpired = false;

    public boolean isExpired() {
        return isExpired;
    }

    public void setExpired() {
        isExpired = true;
    }
}
