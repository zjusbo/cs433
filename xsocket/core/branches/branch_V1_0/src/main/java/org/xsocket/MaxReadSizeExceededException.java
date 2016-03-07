package org.xsocket;

import java.nio.BufferUnderflowException;


/**
 * Unchecked exception thrown when a read operation reaches the predefined limit, 
 * without getting the required data
 * 
 * @author grro
 */
public class MaxReadSizeExceededException extends BufferUnderflowException {

	private static final long serialVersionUID = -1906216307105182850L;

	
	public MaxReadSizeExceededException() {
		
	}

}
