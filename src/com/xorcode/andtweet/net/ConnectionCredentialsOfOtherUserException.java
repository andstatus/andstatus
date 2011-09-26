/**
 * 
 */
package com.xorcode.andtweet.net;

/**
 * @author Yuri
 *
 */
public class ConnectionCredentialsOfOtherUserException extends Exception {
    /**
     * @param newName - Name of the User whose credentials were actually verified
     */
    public ConnectionCredentialsOfOtherUserException(String newName) {
        // Reuse detailMessage of the superclass
        super(newName);
    }

    /**
     * 
     */
    private static final long serialVersionUID = 9140915512294596476L;

}
