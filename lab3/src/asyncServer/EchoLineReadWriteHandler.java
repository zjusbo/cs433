package asyncServer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import server.RequestHandler;
import utility.HTTPRequest;
import utility.HTTPResponse;

public class EchoLineReadWriteHandler implements IReadWriteHandler {

    private ByteBuffer inBuffer;
    private ByteBuffer outBuffer;

    private boolean requestComplete;
    private boolean responseReady;
    private boolean responseSent;
    private boolean channelClosed;
    private volatile boolean channelReadyToClose; // this variable may be modified by handler and timeout thread concurrently
    private StringBuffer request;
	private ITimeoutThread ITimeoutThread = null;

    // private enum State {
    // READ_REQUEST, REQUEST_COMPLETE, GENERATING_RESPONSE, RESPONSE_READY,
    // RESPONSE_SENT
    // }
    // private State state;
	
    public EchoLineReadWriteHandler() {
        inBuffer = ByteBuffer.allocate(4096);
        outBuffer = ByteBuffer.allocate(4096);

        // initial state
        requestComplete = false;
        responseReady = false;
        responseSent = false;
        channelReadyToClose = false;
        channelClosed = false;

        request = new StringBuffer(4096);
    }

    public int getInitOps() {
        return SelectionKey.OP_READ;
    }

    public void handleException() {
    }

    public void handleRead(SelectionKey key) throws IOException {

        // a connection is ready to be read
        Debug.DEBUG("->handleRead");

        if (requestComplete) { // this call should not happen, ignore
            return;
        }

        // process data
        processInBuffer(key);

        // update state
        updateState(key);

        Debug.DEBUG("handleRead->");

    } // end of handleRead

    private void updateState(SelectionKey key) throws IOException {

        Debug.DEBUG("->Update dispatcher.");

        if (channelClosed){
        	Debug.DEBUG("channel closed. It should not be printed.", 1);
            return;
        }

        
        /*
         * if (responseSent) {
         * Debug.DEBUG("***Response sent; shutdown connection"); client.close();
         * dispatcher.deregisterSelection(sk); channelClosed = true; return; }
         */

        int nextState = key.interestOps();
        if (requestComplete) {
            nextState = nextState & ~SelectionKey.OP_READ;
            Debug.DEBUG("New state: -Read since request parsed complete");
        } else {
            nextState = nextState | SelectionKey.OP_READ;
            Debug.DEBUG("New state: +Read to continue to read");
        }

        if (responseReady) {
        	
            if (!responseSent) {
                nextState = nextState | SelectionKey.OP_WRITE;
                Debug.DEBUG("New state: +Write since response ready but not done sent");
            } else {
                nextState = nextState & ~SelectionKey.OP_WRITE;
                channelReadyToClose = true;
                Debug.DEBUG("New state: -Write since response ready and sent");
            }
        }

        key.interestOps(nextState);
        
        if (channelReadyToClose && !channelClosed){
        	Debug.DEBUG("New state: Close channel", 1);
        	
        	((SocketChannel)key.channel()).socket().close();
        	key.channel().close();
        	key.cancel();
        	
        	channelClosed = true;
        	// may remove multiple times, but it doesn't matter
        	if(this.ITimeoutThread != null)
        		this.ITimeoutThread.removeKey(key);
        	return ;
        }
    }
    public void handleWrite(SelectionKey key) throws IOException {
        Debug.DEBUG("->handleWrite");

        // process data
        SocketChannel client = (SocketChannel) key.channel();
        Debug.DEBUG("handleWrite: Write data to connection " + client
                + "; from buffer " + outBuffer);
        int writeBytes = client.write(outBuffer);
        Debug.DEBUG("handleWrite: write " + writeBytes + " bytes; after write " + outBuffer);

        if (responseReady && (outBuffer.remaining() == 0)) {
            responseSent = true;
            client.socket().shutdownOutput(); // close output stream
            Debug.DEBUG("handleWrite: responseSent");
        }

        // update state
        updateState(key);

        // try {Thread.sleep(5000);} catch (InterruptedException e) {}
        Debug.DEBUG("handleWrite->");
    } // end of handleWrite

    private void processInBuffer(SelectionKey key) throws IOException {
        Debug.DEBUG("processInBuffer");
        SocketChannel client = (SocketChannel) key.channel();
        int readBytes = client.read(inBuffer);
        Debug.DEBUG("handleRead: Read data from connection " + client + " for "
                + readBytes + " byte(s); to buffer " + inBuffer);

        if (readBytes == -1) { // end of stream
            requestComplete = true;
            
            Debug.DEBUG("handleRead: readBytes == -1");
        } else {
            inBuffer.flip(); // read input
            // outBuffer = ByteBuffer.allocate( inBuffer.remaining() );
            boolean startLine = true;
            char prech = ' ';
            while (!requestComplete && inBuffer.hasRemaining()
                    && request.length() < request.capacity()) {
                char ch = (char) inBuffer.get();
                Debug.DEBUG("Ch: " + ch, 3);
                request.append(ch);
                if (ch == '\n' && prech == '\r') {
                	if(startLine == true){
                		requestComplete = true;
                        // client.shutdownInput();
                        Debug.DEBUG("handleRead: find terminating chars");
                	}else{
                		startLine = true;
                	}     	
                }
                if(ch != '\n' && ch != '\r'){ 
                	startLine = false;
                }// end if
                prech = ch;
            }// end of while
             
        }

        inBuffer.clear(); // we do not keep things in the inBuffer

        if (requestComplete) {
            generateResponse();
        }

    } // end of process input

    private void generateResponse() {
    	HTTPResponse response = RequestHandler.getResponse(HTTPRequest.parse(request.toString()));
        outBuffer.put(response.getBytes());
        outBuffer.flip();
        responseReady = true;
    } // end of generate response

	@Override
	// cancel current client
	public void cancel() {
		channelReadyToClose = true;
		
	}

	@Override
	public void setTimeoutthread(ITimeoutThread timeoutThread) {
		this.ITimeoutThread  = timeoutThread;
	}
	
	public boolean isChannelClosed() {
		return channelClosed;
	}

	public boolean isChannelReadyToClose(){
		return channelReadyToClose;
	}
}