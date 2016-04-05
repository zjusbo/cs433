import java.io.UnsupportedEncodingException;

/**
 * <pre>   
 * Provides some useful static methods
 * </pre>   
 */
public class Utility {
    
    private static final String CHARSET = "US-ASCII";

    /**
     * Return System time in microseconds
     */
    public static long fishTime() { 
	return System.currentTimeMillis() * 1000;
    }

    /**
     * Convert a string to a byte[]
     * @param msg The string to convert
     * @return The byte[] that the string was converted to
     */
    public static byte[] stringToByteArray(String msg) {
	try {
	    return msg.getBytes(CHARSET);	
	}catch(UnsupportedEncodingException e) {
	    System.err.println("Exception occured while converting string to byte array. String: " + msg + " Exception: " + e);
	}
	return null;
    }
    
    /**
     * Convert a byte[] to a string
     * @param msg The byte[] to convert 
     * @return The converted String
     */
    public static String byteArrayToString(byte[] msg) {
	try {
	    return new String(msg, CHARSET);
	}catch(UnsupportedEncodingException e) {
	    System.err.println("Exception occured while converting byte array to string. Exception: " + e);
	}
	return null;
    }

}
