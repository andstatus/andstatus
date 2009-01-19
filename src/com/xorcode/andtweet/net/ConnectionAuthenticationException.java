/**
 * 
 */
package com.xorcode.andtweet.net;

/**
 * @author tbjers
 *
 */
public class ConnectionAuthenticationException extends Exception {

	private static final long serialVersionUID = -8953640607011219252L;

	/**
	 * @param detailMessage
	 */
	public ConnectionAuthenticationException(String detailMessage) {
		super(detailMessage);
	}

	/**
	 * @param throwable
	 */
	public ConnectionAuthenticationException(Throwable throwable) {
		super(throwable);
	}

	/**
	 * @param detailMessage
	 * @param throwable
	 */
	public ConnectionAuthenticationException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}
}
