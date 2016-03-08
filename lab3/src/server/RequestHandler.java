package server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import asyncServer.Debug;
import utility.HTTPRequest;
import utility.HTTPResponse;

public class RequestHandler {
	static private ServerConfig config;
	static private HashMap<String, byte[]> cache = new HashMap<String, byte[]>();
	static private Double cache_curr_size = new Double(0);

	/**
	 * TODO: Understand header - If-Modified-Since - User-Agent Sender Header: -
	 * Last-Modified Feature: - URL Mapping, / map to index.html or m_index.html
	 * - Check mapping file is executable or not - heart beat monitoring
	 **/
	public static void setConfig(ServerConfig config) {
		RequestHandler.config = config;
	}

	public static void HandleConnectionSocket(Socket connectionSocket) throws IOException {
		byte[] b_buf = new byte[2000];
		int length;

		length = connectionSocket.getInputStream().read(b_buf);
		byte[] b_content = Arrays.copyOfRange(b_buf, 0, length);
		String s_request = new String(b_content, StandardCharsets.US_ASCII);
		/*
		 * while (inFromClient.ready()) { clientSentence =
		 * inFromClient.readLine(); System.out.println(clientSentence);
		 * s_request += clientSentence + "\n"; }
		 */
		HTTPRequest request = HTTPRequest.parse(s_request);
		// process input
		if (request == null) {
			System.err.println("request format error.");
			System.out.println(s_request);
			connectionSocket.close();
			return;
		}
		// generate response
		HTTPResponse response = RequestHandler.getResponse(request);
		if (response == null) {
			System.err.println("Can not generate response");
			connectionSocket.close();
			return;
		}
		// send reply
		// System.out.println(response);
		DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
		Debug.DEBUG("writing response length: " + response.getBytes().length + " bytes to socket");
		//outToClient.writeBytes(response.toString());
		outToClient.write(response.getBytes());
		Debug.DEBUG("close socket");
		connectionSocket.close();
	}

	public static HTTPResponse getResponse(HTTPRequest request) {

		String host = request.getHost();
		String url = request.getURL();
		if (!host.equals(config.servername)) {
			// host name not match
			return new HTTPResponse(404);
		}
		String rootDocument = config.documentRoot;

		// original url ends '/', map it to index.html
		if (url.endsWith("/")) {
			url += "index.html";
		}
		// trim heading "/"
		if (url.startsWith("/")) {
			url = url.substring(1);
		}

		// trim trailing "/"
		if (rootDocument.endsWith("/")) {
			rootDocument = rootDocument.substring(0, rootDocument.length() - 1);
		}

		String file_path = rootDocument + "/" + url;

		byte[] file_content = null;
		// if file_path executable?
		
		if (file_path.endsWith(".cgi")) {
			Debug.DEBUG("Start cgi program..");
			ProcessBuilder pb = new ProcessBuilder("python", file_path);
			Map<String, String> env = pb.environment();
			// env.put("INDEX", Integer.toString(reqCount));
			pb.redirectOutput(Redirect.PIPE);
			Process p;
			try {
				p = pb.start();

				BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
				StringBuilder out = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					out.append(line);
				}
				file_content = out.toString().getBytes();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			// regular file
			// read from cache
			synchronized (cache) {
				file_content = cache.get(file_path);
				if(file_content != null)
					Debug.DEBUG("cache hit: " + file_path, 2);
			}
			// read from disk, update cache if cachesize is not reached
			if (file_content == null) {
				file_content = readFile(file_path);
				// file found
				if (file_content != null) {
					// thread safe, modify cache_size and cache
					synchronized (cache) {
						double cache_size = RequestHandler.cache_curr_size
								+ (double) (file_path.length() + file_content.length) / 1024;
						// cache not full
						if (cache_size < Integer.valueOf(config.cacheSize)) {
							Debug.DEBUG("update cache: " + cache_size + " kB, max = " + Integer.valueOf(config.cacheSize) + " kB");
							cache.put(file_path, file_content);
							RequestHandler.cache_curr_size = cache_size;
						}else{
							Debug.DEBUG("Cache is full");
						}
					}
				}else{
					Debug.DEBUG("file: " + file_path + " does not exist");
				}

			}
		}

		// file not found
		if (file_content == null){
			return new HTTPResponse(404);
		}
		Debug.DEBUG("gen 200 response");
		return new HTTPResponse(200, file_content);
	}

	// ------ //

	/**
	 * TODO Support Since-last-modify header
	 **/

	private static byte[] readFile(String path) {
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
