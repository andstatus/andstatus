package org.andstatus.app.net;

import android.text.TextUtils;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author Yuri Volkov
 */
public class MbUser {
    public String oid="";
    public String userName="";
    public String realName="";
    public String avatarUrl="";
    public String description="";
    public String homepage="";
    public long createdDate = 0;
    public long updatedDate = 0;
    public MbMessage latestMessage = null;    

    public MbUser reader = null;
    public Boolean followedByReader = null;
    
    // In our system
    public long originId = 0L;
    
    public static MbUser fromOriginAndUserOid(long originId, String userOid) {
        MbUser user = new MbUser();
        user.originId = originId;
        user.oid = userOid;
        return user;
    }
    
    public static MbUser getEmpty() {
        MbUser user = new MbUser();
        return user;
    }
    
    private MbUser() {}
    
    public boolean isEmpty() {
        return ((TextUtils.isEmpty(userName) && TextUtils.isEmpty(oid)) || originId==0);
    }
}
