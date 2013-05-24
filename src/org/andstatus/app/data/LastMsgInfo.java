package org.andstatus.app.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.andstatus.app.MyService;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;

import java.util.Date;


/**
 * Retrieve and save information about the last downloaded message from this timeline
 */
public class LastMsgInfo {
    private static final String TAG = LastMsgInfo.class.getSimpleName();

    private MyAccount ma;
    private ContentResolver mContentResolver;
    private TimelineTypeEnum mTimelineType;
    /**
     * The timeline parameter. Used in the {@link TimelineTypeEnum#USER} timeline.
     */
    private long mUserId = 0;
    
    
    boolean mLastMsgInfoRetrieved = false;
    /**
     * 0 - none were downloaded
     */
    long mLastMsgId = 0;
    /**
     * {@link MyDatabase.Msg#SENT_DATE} of the {@link #mLastMsgId}
     */
    long mLastMsgDate = 0;
    /**
     * Last date when this timeline was successfully downloaded
     */
    long mTimelineDate = 0;
    
    /**
     * Retrieve information about the last downloaded message from this timeline
     */
    public LastMsgInfo(MyAccount ma_in, Context context, TimelineTypeEnum timelineType, long userId_in) {
        ma = ma_in;
        mContentResolver = context.getContentResolver();
        mTimelineType = timelineType;
        mUserId = userId_in;
        
        if (!mLastMsgInfoRetrieved) {
            long userId = mUserId;
            if (mTimelineType != TimelineTypeEnum.USER) {
                userId = ma.getUserId();
            }
            mTimelineDate = MyProvider.userIdToLongColumnValue(mTimelineType.columnNameTimelineDate(), userId);
            mLastMsgId = MyProvider.userIdToLongColumnValue(mTimelineType.columnNameLatestMsgId(), userId);
            mLastMsgDate = MyProvider.msgSentDate(mLastMsgId);
            if (mLastMsgId > 0 && mLastMsgDate == 0) {
                MyLog.d(TAG, "There is no message with " + MyDatabase.Msg._ID + "=" + mLastMsgId + " yet"); 
                mLastMsgId = 0;
            }
            
        }
    }
    
    /**
     * @return Id of the last downloaded message from this timeline
     */
    public long getLastMsgId() {
        return mLastMsgId;
    }
    /**
     * @return Date of the last downloaded message 
     */
    public long getLastMsgDate() {
        return mLastMsgDate;
    }

    /**
     * @return Last date when this timeline was successfully downloaded
     */
    public long getTimelineDate() {
        return mTimelineDate;
    }

    /**
     * Actually we're saving Timeline date also in order to know when it will be time 
     * for the next automatic update
     */
    public void saveLastMsgInfo(long lastMsgId, long lastMsgDate) {
        mLastMsgId = lastMsgId;
        mLastMsgDate = lastMsgDate;
        mTimelineDate = System.currentTimeMillis();
        long userId = mUserId;
        if (mTimelineType != TimelineTypeEnum.USER) {
            userId = ma.getUserId();
        }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            MyLog.v(TAG, "Timeline " + mTimelineType.save() 
                    + " for the user=" + MyProvider.userIdToName(userId) 
                    + " downloaded at " + (new Date(getTimelineDate()).toString()));
        }

        try {
            ContentValues values = new ContentValues();
            values.put(mTimelineType.columnNameTimelineDate(), mTimelineDate );
            values.put(mTimelineType.columnNameLatestMsgId(), mLastMsgId );
            Uri userUri = MyProvider.getUserUri(ma.getUserId(), userId);
            mContentResolver.update(userUri, values, null, null);
        } catch (Exception e) {
            Log.e(TAG, "saveLastMsgInfo: " + e.toString());
        }
    
    }
    
    /**
     * @return true if it's time to auto update this timeline
     */
    public boolean itsTimeToAutoUpdate() {
        boolean blnOut = MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_AUTOMATIC_UPDATES, false);
        if (!blnOut) {
            // Automatic updates are disabled
            return false;
        }
        long intervalMs = Integer.parseInt(MyPreferences.getDefaultSharedPreferences().getString(MyPreferences.KEY_FETCH_PERIOD, "180")) * MyService.MILLISECONDS;
        long passedMs = System.currentTimeMillis() - getTimelineDate(); 
        blnOut = (passedMs > intervalMs);
        
        if (blnOut && MyLog.isLoggable(TAG, Log.VERBOSE)) {
            long userId = mUserId;
            if (mTimelineType != TimelineTypeEnum.USER) {
                userId = ma.getUserId();
            }
            MyLog.v(TAG, "It's time to auto update " + mTimelineType.save() 
                    + " for the user=" + MyProvider.userIdToName(userId)
                    + ". Minutes passed=" + passedMs/1000/60);
        }
        return blnOut;
    }
}
