

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;

public class PingPackage {
	private static final String request_signature = "PING";
	private static final String response_signature = "PINGECHO";
    public String signature;
    public short sequence_number;
    public long client_send_time;
    public String passwd;
    public PingPackage(){
    	signature = request_signature;
    	sequence_number = 0;
    	client_send_time = System.currentTimeMillis();
    	passwd = "";
    }
    public long getTimestamp(){
    	return client_send_time;
    }
    public void setSequence_number(short n){
    	sequence_number = n;
    }
    public void setPasswd(String passwd){
    	this.passwd = passwd;
    }
    public void parse(byte[] data) throws DataFormatException{
      if(data.length < 16){
    	  throw new DataFormatException("Data length is less than 16");
      }
      // wary byte array
      ByteBuffer bytebuf = ByteBuffer.wrap(data);
      // big endian is network endian
      bytebuf.order(ByteOrder.BIG_ENDIAN);
      
      // try to parse PINGECHO
      byte[] buf = new byte[8];
      bytebuf.get(buf);
      signature = new String(buf, StandardCharsets.US_ASCII);
      if(!signature.equals(response_signature)){
    	  // try to parse PING
    	  buf = new byte[4];
    	  bytebuf.position(0);
    	  bytebuf.get(buf);
          signature = new String(buf, StandardCharsets.US_ASCII);
          if(!signature.equals(request_signature)){
        	  throw new DataFormatException("Signature validation failed: "+ signature);
          }
      }
      
      //parse sequence_number
      sequence_number = bytebuf.getShort();
      
      //parse client_send_time
      client_send_time = bytebuf.getLong();
      buf = new byte[bytebuf.remaining()];	
      bytebuf.get(buf);
      String str = new String(buf, StandardCharsets.US_ASCII);

      int end = str.indexOf("\r\n");
      if(end != -1){
    	  passwd = str.substring(0, end);
      }else{
    	  throw new DataFormatException("Package format error. Package should end with \\r\\n");
      }
    }
    public void toResponse(){
    	signature = response_signature;
    }
    public boolean isRequest(){
    	return signature.equals(request_signature);
    }
    public boolean isResponse(){
    	return signature.equals(response_signature);
    }
    
    @Override
    public String toString(){
    	return String.format("cmd: " + signature + ", seq: " + sequence_number + ", timestamp: " + client_send_time + ", passwd: " + passwd);
    }
    public byte[] getBytes(){
    	ByteBuffer bytebuf;
    	bytebuf = ByteBuffer.allocate(100);
    	bytebuf.order(ByteOrder.BIG_ENDIAN);
    	bytebuf.put(signature.getBytes(StandardCharsets.US_ASCII));
    	bytebuf.putShort(sequence_number);
    	bytebuf.putLong(client_send_time);
    	bytebuf.put(passwd.getBytes(StandardCharsets.US_ASCII));
    	bytebuf.put("\r\n".getBytes(StandardCharsets.US_ASCII));
    	return bytebuf.array();
    }
}
