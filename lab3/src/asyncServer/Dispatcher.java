package asyncServer;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
// for Set and Iterator
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Dispatcher implements Runnable {
	private TimeoutThread timeoutThread;
    private Selector selector;
    private volatile List<SelectionKey> skList = new ArrayList<SelectionKey>();
    
    public Dispatcher(TimeoutThread timeoutThread) {
        // create selector
    	this.timeoutThread = timeoutThread;
    	this.timeoutThread.setRemovedList(skList);
        try {
            selector = Selector.open();
        } catch (IOException ex) {
            System.out.println("Cannot create selector.");
            ex.printStackTrace();
            System.exit(1);
        } // end of catch
    } // end of Dispatcher

    public Selector selector()  {
        return selector;
    }
    /*
    public SelectionKey registerNewSelection(SelectableChannel channel,
            IChannelHandler handler, int ops) throws ClosedChannelException {
        SelectionKey key = channel.register(selector, ops);
        key.attach(handler);
        return key;
    } // end of registerNewChannel

    public SelectionKey keyFor(SelectableChannel channel) {
        return channel.keyFor(selector);
    }

    public void deregisterSelection(SelectionKey key) throws IOException {
        key.cancel();
    }

    public void updateInterests(SelectionKey sk, int newOps) {
        sk.interestOps(newOps);
    }
*/
    
    public void run() {
    	
        while (true) {
            Debug.DEBUG("Enter selection");
            try {
                // check to see if any events
                selector.select();        
                Debug.DEBUG("Exit selection");
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }

            // try to remove timeout keys
            Iterator<SelectionKey> it = skList.iterator();
            while(it.hasNext()){
            	SelectionKey sk = (SelectionKey) it.next();
            	it.remove();
            	sk.cancel();
            	Debug.DEBUG("remove key!");
            }
            
            
            // readKeys is a set of ready events
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            
            // create an iterator for the set
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            // iterate over all events
            while (iterator.hasNext()) {

                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();
              
                try {
                    if (key.isAcceptable()) { // a new connection is ready to be
                                              // accepted
                        IAcceptHandler aH = (IAcceptHandler) key.attachment();
                        aH.handleAccept(key);
                    } // end of isAcceptable

                    if (key.isReadable() || key.isWritable()) {
                        IReadWriteHandler rwH = (IReadWriteHandler) key
                                .attachment();

                        if (key.isReadable()) {
                            rwH.handleRead(key);
                        } // end of if isReadable

                        if (key.isWritable()) {
                            rwH.handleWrite(key);
                        } // end of if isWritable
                    } // end of readwrite
                } catch (IOException ex) {
                    Debug.DEBUG("Exception when handling key " + key);
                    key.cancel();
                    try {
                        key.channel().close();
                        // in a more general design, call have a handleException
                    } catch (IOException cex) {
                    }
                } // end of catch

            } // end of while (iterator.hasNext()) {

        } // end of while (true)
    } // end of run
}