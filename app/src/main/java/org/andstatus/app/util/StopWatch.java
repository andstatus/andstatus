package org.andstatus.app.util;

/**
 * @author yvolk@yurivolkov.com
 */
public class StopWatch extends org.apache.commons.lang3.time.StopWatch {

    public static StopWatch createStarted() {
        final StopWatch sw = new StopWatch();
        sw.start();
        return sw;
    }

    public boolean hasPassed(long millis) {
        if (getTime() > millis) {
            restart();
            return true;
        }
        return false;
    }

    private void restart() {
        reset();
        start();
    }
}
