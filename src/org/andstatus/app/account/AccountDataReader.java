package org.andstatus.app.account;

import org.andstatus.app.data.MyDatabase;


/**
 * Interface that allows to read the {@link MyAccount}'s persistent data (including account's connection data) 
 */
public interface AccountDataReader {

    public boolean dataContains(String key);
    
    /**
     * @param key Key Name
     * @param defValue Default value
     * @return Returns null only in case defValue is null
     */
    public String getDataString(String key, String defValue);

    public int getDataInt(String key, int defValue);    
}
