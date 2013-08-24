package org.andstatus.app.net;

/**
 * Information of the Microblogging system 
 * of how many more requests are allowed 
 * and the limit of such requests
 */
public class MbRateLimitStatus {
    public int remaining = 0;
    public int limit = 0;
    
    public boolean isEmpty() {
      return (limit == 0 && remaining == 0);   
    }
}
