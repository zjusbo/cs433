package utility;


import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

public class HTTPResponse {
	static final String protocol = "HTTP/1.0";
	static final String CRLF = "\r\n";
	static final String content_type = "text/html"; // It is fixed for now
	static final String [] field_labels = {"Date:", "Server:", "Content-Type:", "Content-Length:"};
	static final HashMap<Integer, String> m_message;
	static String servername;
	static{
		m_message = new HashMap<Integer, String>();
		m_message.put(200, "OK");
		m_message.put(404, "NOT FOUND");
		m_message.put(405, "Method Not Allowed");
		
	}
	private int statusCode;
	/**
	 * TODO
	 *  Support media data
	 *  Change file_content from string to byte[]
	 **/
	private byte[] file_content; 
	private HashMap<String, String> m_fields = new HashMap<String, String>();
	
	public HTTPResponse(int code, byte[] file_content){
		this.statusCode = code;
		
		this.file_content = file_content;
		if(HTTPResponse.servername == null){
			// servername is not initialized
			throw new NullPointerException("Servername is not set");
		}
		int content_length;
		if(this.file_content == null){
			content_length = 0;
		}else{
			content_length = this.file_content.length;		
		}
		
		this.m_fields.put(HTTPResponse.field_labels[0], HTTPResponse.getServerTime()); // Date
		this.m_fields.put(HTTPResponse.field_labels[1], HTTPResponse.servername); // Server
		this.m_fields.put(HTTPResponse.field_labels[2], HTTPResponse.content_type); // content-type
		this.m_fields.put(HTTPResponse.field_labels[3], String.valueOf(content_length));
	}
	
	// for situation that the response does not contain any file content
	public HTTPResponse(int code){
		this(code, null);
	}
	
	public static void setServername(String name){
		HTTPResponse.servername = name;
	}
	@Override
	public String toString(){
		String s = new String();
		String message = HTTPResponse.m_message.get(this.statusCode);
		s = HTTPResponse.protocol + " " + this.statusCode + " " + message + HTTPResponse.CRLF;
		for(String label : field_labels){
			s += label + " " + m_fields.get(label) + HTTPResponse.CRLF;
		}
		s += HTTPResponse.CRLF;
		if(this.file_content != null){
			s += new String(this.file_content, StandardCharsets.US_ASCII); // will here be a bug?
		}
		//Debug.DEBUG(s);
		return s;
	}
	
	public byte[] getBytes(){
		return this.toString().getBytes(StandardCharsets.US_ASCII);
	}
	
	//http://stackoverflow.com/questions/7707555/getting-date-in-http-format-in-java
	static String getServerTime() {
	    Calendar calendar = Calendar.getInstance();
	    SimpleDateFormat dateFormat = new SimpleDateFormat(
	        "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
	    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	    return dateFormat.format(calendar.getTime());
	}
}
