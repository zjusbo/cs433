package org.xsocket.bayeux.http;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.connection.IConnection.FlushMode;
import org.xsocket.connection.http.AbstractBodyForwarder;
import org.xsocket.connection.http.BodyDataSink;
import org.xsocket.connection.http.IHttpExchange;
import org.xsocket.connection.http.IHttpHeader;
import org.xsocket.connection.http.IHttpRequest;
import org.xsocket.connection.http.IHttpRequestHandler;
import org.xsocket.connection.http.IHttpRequestHeader;
import org.xsocket.connection.http.IHttpResponseHeader;
import org.xsocket.connection.http.IHttpResponse;
import org.xsocket.connection.http.IHttpResponseHandler;
import org.xsocket.connection.http.NonBlockingBodyDataSource;


@Execution(Execution.NONTHREADED)
public final class LogFilter implements IHttpRequestHandler {
	
	
	
	public void onRequest(final IHttpExchange exchange) throws IOException {
		
		IHttpRequest req = exchange.getRequest(); 

		
		IHttpResponseHandler respHdl = new IHttpResponseHandler() {
			
			@Execution(Execution.NONTHREADED)
			public void onResponse(IHttpResponse response) throws IOException {
 
				// does request contain a body? 
				if (response.hasBody()) {
					
					final IHttpResponseHeader header = response.getResponseHeader(); 
					final List<ByteBuffer> bodyData = new ArrayList<ByteBuffer>(); 
					
					NonBlockingBodyDataSource orgDataSource = response.getNonBlockingBody();
					final BodyDataSink inBodyChannel = exchange.send(response.getResponseHeader());
					
					//... by a body forward handler
					AbstractBodyForwarder bodyForwardHandler = new AbstractBodyForwarder(orgDataSource, inBodyChannel) {
						
						@Override
						public void onData(NonBlockingBodyDataSource bodyDataSource, BodyDataSink bodyDataSink) throws BufferUnderflowException, IOException {
							ByteBuffer[] bufs = bodyDataSource.readByteBufferByLength(bodyDataSource.available());
									
							for (ByteBuffer byteBuffer : bufs) {
								bodyData.add(byteBuffer.duplicate());
							}
									
							bodyDataSink.write(bufs);
							bodyDataSink.flush();
						}
						
						@Override
						public void onComplete() {
							printMessage(header, bodyData);
						}
					};
					orgDataSource.setDataHandler(bodyForwardHandler);
 					
				} else {
					try {
						System.out.println(response.getResponseHeader());
					} catch (Exception ignore) { }
					exchange.send(response);
				}
			}
			
			public void onException(IOException ioe) {
				exchange.sendError(500);
			}
		};
		
		
		// does request contain a body? 
		if (req.hasBody()) {
			
			final IHttpRequestHeader header = req.getRequestHeader(); 
			final List<ByteBuffer> bodyData = new ArrayList<ByteBuffer>(); 
 
			
			// get the body 
			NonBlockingBodyDataSource orgDataSource = req.getNonBlockingBody();
			
			// ... and replace it  
			final BodyDataSink inBodyChannel = exchange.forward(req.getRequestHeader(), respHdl);
			
			//... by a body forward handler
			AbstractBodyForwarder bodyForwardHandler = new AbstractBodyForwarder(orgDataSource, inBodyChannel) {
				
				@Override
				public void onData(NonBlockingBodyDataSource bodyDataSource, BodyDataSink bodyDataSink) throws BufferUnderflowException, IOException {
					ByteBuffer[] bufs = bodyDataSource.readByteBufferByLength(bodyDataSource.available());
							
					for (ByteBuffer byteBuffer : bufs) {
						 bodyData.add(byteBuffer.duplicate());
					}
							
					bodyDataSink.write(bufs);
					bodyDataSink.flush();
				}
				
				@Override
				public void onComplete() {
					printMessage(header, bodyData);
				}
			};
			orgDataSource.setDataHandler(bodyForwardHandler);
			
		} else {
			System.out.println(req.getRequestHeader().toString());
			exchange.forward(req, respHdl);
		} 
	}
	
	
	private void printMessage(IHttpHeader header, List<ByteBuffer> bodyData) {
	
		try {
			String body = DataConverter.toString(bodyData, header.getCharacterEncoding());
			String message = parseFormParamters(header, body).get("message");
			
			System.out.println(header.toString());
			if (message != null) {
				System.out.println(message);
			} else {
				System.out.println(body + "\r\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	
	static Map<String, String> parseFormParamters(IHttpHeader header, String body) throws IOException {
		Map<String, String> result = new HashMap<String, String>();
		
		String contentType = header.getContentType();
		if (contentType.startsWith("application/x-www-form-urlencoded")) {
			String[] params = body.split("&");
			for (String param : params) {
				String[] kv = param.split("=");
				result.put(kv[0], URLDecoder.decode(kv[1], "UTF-8"));
			}
		}
		
		return result;
	}

}
