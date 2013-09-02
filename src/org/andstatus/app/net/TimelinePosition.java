package org.andstatus.app.net;

import android.text.TextUtils;

/**
 * Since introducing support for Pump.Io it appeared that 
 * Position in the Timeline and Id of the Message may be different things.
 * @author Yuri Volkov
 */
public class TimelinePosition {
    private final String position;

    public TimelinePosition(String position) {
        if (TextUtils.isEmpty(position)) {
            position = "";
        }
        this.position = position;
    }

    public String getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return position;
    }

    public static TimelinePosition getEmpty() {
        TimelinePosition position = new TimelinePosition("");
        return position;
    }

    public boolean isEmpty() {
        return (TextUtils.isEmpty(position));
    }
    
}
