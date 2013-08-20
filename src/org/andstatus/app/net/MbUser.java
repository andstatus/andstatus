package org.andstatus.app.net;

import org.json.JSONObject;

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
    public boolean isFollowed = false;
    
    // In our system
    public long originId = 0L;
    public long rowId = 0L;
}
