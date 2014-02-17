package org.andstatus.app;

import android.content.Context;

import org.andstatus.app.data.TimelineTypeEnum;

public class TimelineTypeSelector {
    private static TimelineTypeEnum[] timelineTypes = {
            TimelineTypeEnum.HOME,
            TimelineTypeEnum.FAVORITES,
            TimelineTypeEnum.MENTIONS,
            TimelineTypeEnum.DIRECT,
            TimelineTypeEnum.USER,
            TimelineTypeEnum.FOLLOWING_USER,
            TimelineTypeEnum.PUBLIC
    };
    
    private Context context;
    
    public TimelineTypeSelector(Context context) {
        super();
        this.context = context;
    }

    public String[] getTitles() {
        String[] titles = new String[timelineTypes.length];
        for (int ind=0; ind < timelineTypes.length; ind++) {
            titles[ind] = context.getString(timelineTypes[ind].getTitleResId());
        }
        return titles;
    }

    public TimelineTypeEnum positionToType(int position) {
        TimelineTypeEnum type = TimelineTypeEnum.UNKNOWN;
        if (position >= 0 && position < timelineTypes.length) {
            type = timelineTypes[position];
        }
        return type;
    }

    public static TimelineTypeEnum selectableType(TimelineTypeEnum typeSelected) {
        TimelineTypeEnum typeSelectable = TimelineTypeEnum.HOME;
        for (TimelineTypeEnum type : timelineTypes) {
            if (type == typeSelected) {
                typeSelectable = typeSelected;
                break;
            }
        }
        return typeSelectable;
    }
}
