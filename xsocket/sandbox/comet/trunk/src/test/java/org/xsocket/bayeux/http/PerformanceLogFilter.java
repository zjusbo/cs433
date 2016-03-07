package org.xsocket.bayeux.http;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


import org.xsocket.Execution;
import org.xsocket.connection.http.AbstractBodyForwarder;
import org.xsocket.connection.http.BodyDataSink;
import org.xsocket.connection.http.IHttpExchange;
import org.xsocket.connection.http.IHttpRequestHandler;
import org.xsocket.connection.http.IHttpRequestHeader;
import org.xsocket.connection.http.IHttpResponseHeader;
import org.xsocket.connection.http.IHttpResponse;
import org.xsocket.connection.http.IHttpResponseHandler;
import org.xsocket.connection.http.NonBlockingBodyDataSource;


@Execution(Execution.NONTHREADED)
public final class PerformanceLogFilter implements IHttpRequestHandler {
	
	private static final SimpleDateFormat DF = new SimpleDateFormat("HH:mm:ss,S"); 
	
	private final TraceLog traceLog = new TraceLog(100);
	private int lastCallTime = 0;

	
	
	public void onRequest(final IHttpExchange exchange) throws IOException {
		
		IHttpResponseHandler respHdl = new IHttpResponseHandler() {
			
			private long start = System.currentTimeMillis();
			private IHttpRequestHeader requestHeader = exchange.getRequest().getRequestHeader();
			
			@Execution(Execution.NONTHREADED)
			public void onResponse(IHttpResponse response) throws IOException {
 
				// does request contain a body? 
				if (response.hasBody()) {
					
					final IHttpResponseHeader header = response.getResponseHeader(); 
					
					NonBlockingBodyDataSource orgDataSource = response.getNonBlockingBody();
					final BodyDataSink inBodyChannel = exchange.send(response.getResponseHeader());
					
					//... by a body forward handler
					AbstractBodyForwarder bodyForwardHandler = new AbstractBodyForwarder(orgDataSource, inBodyChannel) {
						
						@Override
						public void onComplete() {
							onCallCompleted(requestHeader, header, start);
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
		
		
		exchange.forward(exchange.getRequest(), respHdl);
	}
	
	 
	
	private void onCallCompleted(IHttpRequestHeader requestHeader, IHttpResponseHeader responseHeader, long startTime) {
		lastCallTime = (int) (System.currentTimeMillis() - startTime);
		StringBuilder sb = new StringBuilder("[" + DF.format(new Date()) + "] " + lastCallTime + " millis: " + requestHeader.getMethod() + " " + requestHeader.getRequestURI());
		if (requestHeader.getQueryString() != null) {
			sb.append("?" + requestHeader.getQueryString());
		}
		sb.append(" -> " + responseHeader.getStatus() + " " + responseHeader.getReason());
		traceLog.addLog(sb.toString());
	}
	
	
	int getLastTime() {
		return lastCallTime;
	}
	
	List<String> getTrace() {
		return traceLog.getEntries();
	}
	
	int getTraceLogMaxSize() {
		return traceLog.getMaxSize();
	}
	
	void setTraceLogMaxSize(int traceLogMaxSize) {
		traceLog.setMaxSize(traceLogMaxSize);
	}
	
	private static final class TraceLog {
		
		private final LinkedList<String> trace = new LinkedList<String>();
		private int maxSize = 0;
		
		public TraceLog(int maxSize) {
			this.maxSize = maxSize;
		}
		
		void setMaxSize(int maxSize) {
			this.maxSize = maxSize;
		}
		
		int getMaxSize() {
			return maxSize;
		}
		
		void addLog(String msg) {
			trace.addFirst(msg);
			
			while (trace.size() > maxSize) {
				trace.removeLast();
			}
		}
		
		List<String> getEntries() {
			return Collections.unmodifiableList(trace);
		}
	}
}
