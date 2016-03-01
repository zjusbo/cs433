import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
public class RequestHandler{
	static private HashMap<String, String> config;
	static private HashMap<String, byte[]> cache;
	static private Double cache_curr_size;
	/** TODO: 
	 * Understand header 
	 *  - If-Modified-Since
	 *  - User-Agent
	 * Sender Header:
	 *  - Last-Modified
	 * Feature:
	 *  - URL Mapping, / map to index.html or m_index.html
	 *  - Check mapping file is executable or not
	 *  - heart beat monitoring
	 **/
	public static void setConfig(HashMap<String, String> config){
		RequestHandler.config = config;
	}
	public static void setCache(HashMap<String, byte[]> cache){
		RequestHandler.cache = cache;
	}
	public static void setCachesize(Double size){
		RequestHandler.cache_curr_size = size;
	}
	public static HTTPResponse getResponse(HTTPRequest request){
		
		String host = request.getHost();
		String url = request.getURL();
		if(!host.equals(config.get("servername"))){
			// host name not match
			return new HTTPResponse(404);
		}
		String rootDocument = config.get("documentroot");
		// trim heading "/"
		if(url.startsWith("/")){
			url = url.substring(1);
		}
		
		// trim trailing "/"
		if(rootDocument.endsWith("/")){
			rootDocument = rootDocument.substring(0, rootDocument.length() - 1);
		}
		
		String file_path = rootDocument + "/" + url;
		byte[] file_content;
		/**
		 * TODO
		 * Support Since-last-modify header
		 **/
		// no cache
		if(cache == null){
			file_content = readFile(file_path);
		}else{
			// read from cache
			synchronized(cache){
				file_content = cache.get(file_path);
			}
			// read from disk, update cache if cachesize is not reached
			if(file_content == null){
				file_content = readFile(file_path);
				// file not found
				if(file_content != null){
					// thread safe, modify cache_size and cache
					synchronized(cache){
						double cache_size = RequestHandler.cache_curr_size + (double)(file_path.length() + file_content.length) / 1024;
						// cache not full
						if( cache_size < Integer.valueOf(config.get("cachesize"))){
							cache.put(file_path, file_content);
							RequestHandler.cache_curr_size = cache_size;
						}
					}					
				}
				
			}
		}
		// file not found
		if(file_content == null){
			return new HTTPResponse(404);
		}
		return new HTTPResponse(200, file_content);
	}
	

	
	private static byte[] readFile(String path){
		try {
			FileInputStream inputStream = new FileInputStream(path);
			int length = inputStream.available();
			byte[] content = new byte[length];
			inputStream.read(content);
			inputStream.close();
			return content;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// file do not exist
			e.printStackTrace();
			return null;
		}
	}
}
