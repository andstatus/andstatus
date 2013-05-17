package org.andstatus.app.account;


/**
 * Interface that allows to read and write the {@link MyAccount}'s persistent data 
 */
public interface AccountDataWriter extends AccountDataReader {
    public void setDataInt(String key, int value);
    public void setDataString(String key, String value);
}
