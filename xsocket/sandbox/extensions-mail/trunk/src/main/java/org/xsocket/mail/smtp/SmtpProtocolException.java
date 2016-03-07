package org.xsocket.mail.smtp;

import java.io.IOException;

public class SmtpProtocolException extends IOException {

	private static final long serialVersionUID = 3002424568613000406L;

	private int smptErrorCode = 0;

	/**
	 * constructor
	 * 
	 * @param smptErrorCode the smtp error code for this error
	 * @param text the error message
	 */
	public SmtpProtocolException(int smptErrorCode, String text) {
		super(text);
		this.smptErrorCode = smptErrorCode;
	}
	
	/**
	 * get the smtp error code
	 * 
	 * @return the smtp error code
	 */
	public int getSmtpErrorCode() {
		return smptErrorCode;
	}

}
