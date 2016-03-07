package distributedcache;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.group.Address;
import org.xsocket.group.IGroupEndpoint;
import org.xsocket.group.ObjectMessage;



final class MultiRpcCaller {
	
	private static final Logger LOG = Logger.getLogger(MultiRpcCaller.class.getName());

	private IGroupEndpoint endpoint = null;
	private final Map<Long, Call> openRequests = Collections.synchronizedMap(new HashMap<Long, Call>()); 
		
	MultiRpcCaller(IGroupEndpoint endpoint) {
		this.endpoint = endpoint;
	}
	
	
	List<Response> call(Request request) throws IOException {
		Set<Address> peers = endpoint.getPeerAddresses();
		return call(request, peers.toArray(new Address[peers.size()]));
	}
		
	
	List<Response> call(Request request, Address... destinationAddresses) throws IOException {
		Call call = new Call(request, destinationAddresses);
		return call.perform();
	}


	boolean handleResponse(ObjectMessage responseMsg) throws IOException {
		if (responseMsg.getObject() instanceof Response) {
			Response response = (Response) responseMsg.getObject();
			Call call = openRequests.get(response.getRequestMsgId());
			if (call != null) {
				call.registerResponse(response, responseMsg.getSourceAddress());
			} else {
				LOG.warning("got response message for unknown request (msgID=" + response.getRequestMsgId() + ")");
			}
			return true;
			
		} else {
			return false;
		}
	}

	
	interface Request extends Serializable {
		
	}

	
	interface Response extends Serializable {
		long getRequestMsgId();
	}
	
	private final class Call {
		
		private final List<Address> notYetResponded = new ArrayList<Address>();
		private final List<Response> responses = new ArrayList<Response>();
		private ObjectMessage requestMsg = null;
		
		Call(Request request, Address... destinationAddresses) throws IOException {			
			this.notYetResponded.addAll(Arrays.asList(destinationAddresses));
			
			requestMsg = endpoint.createObjectMessage(request);
			for (Address address : destinationAddresses) {
				requestMsg.addDestinationAddress(address);
			}
		}
		
		synchronized List<Response> perform() throws IOException {
			openRequests.put(requestMsg.getId(), this);
			
			endpoint.send(requestMsg);
			
			while (!notYetResponded.isEmpty()) {
				try {
					wait();
				} catch (InterruptedException ignore) { }
			} 
			
			openRequests.remove(this);
			return responses;
		}
		
		synchronized void registerResponse(Response response, Address sourceAddress) {
			responses.add(response);
			notYetResponded.remove(sourceAddress);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("got response for msgId " + response.getRequestMsgId()+ " waiting for remaining " + notYetResponded.size());
			}
			
			if (notYetResponded.isEmpty()) {
				notifyAll();
			}
		}
	}
}
