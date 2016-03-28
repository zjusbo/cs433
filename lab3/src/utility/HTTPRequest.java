package utility;


import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class HTTPRequest {
	final static String method = "GET";
	final static String host_label = "HOST:";
	final static String protocol = "HTTP/1.0";
	final static String CRLF = "\r\n";
	private String host = "";
	private String url = "/";

	public HTTPRequest(){}
	
	public HTTPRequest(String url){
		this.url = url;
	}
	
	public HTTPRequest(String url, String host){
		this.url = url;
		this.host = host;
	}
	
	// construct header from HTTP plain text
	public static HTTPRequest parse(String s){
		Scanner scanner = new Scanner(s);
		try{
			String host, host_label;
			String method = scanner.next().toUpperCase();
			String url = scanner.next();
			String protocol = scanner.next().toUpperCase();
			/*if(!method.equals(HTTPRequest.method) || !protocol.equals(HTTPRequest.protocol)){
				throw new NoSuchElementException("Unknown Method or Protocol.");
			}*/
			if(scanner.hasNext()){
				host_label = scanner.next().toUpperCase();
				host = scanner.next();
				if(!host_label.equals(HTTPRequest.host_label)){
					throw new NoSuchElementException("Unknown header field.");
				}
				return new HTTPRequest(url, host);
			}else{
				return new HTTPRequest(url);
			}
			
		}catch(NoSuchElementException e){
			System.err.println(e.getMessage());
			return null;
		}
		finally{
			scanner.close();
		}
		
	}
	
	public String getURL(){
		return this.url;
	}
	
	public String getHost(){
		return this.host;
	}
	
	@Override
	public String toString(){
		String text = "";
		text = HTTPRequest.method + " " + this.url + " " + HTTPRequest.protocol + HTTPRequest.CRLF;
		// append host field
		if(!this.host.isEmpty()){
			text = text + "Host: " + this.host + HTTPRequest.CRLF;
		}
		
		// end of header
		text = text + HTTPRequest.CRLF;
		return text;
	}
	
	public byte[] getBytes(){
		// ASCII CODE, no warry about the endian order
		return this.toString().getBytes(StandardCharsets.US_ASCII);
	}
}
