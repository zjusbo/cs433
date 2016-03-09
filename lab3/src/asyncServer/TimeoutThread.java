package asyncServer;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

import utility.Debug;

public class TimeoutThread extends Thread implements ITimeoutThread{
	private int timeout; // timeout in mili seconds
	private boolean running = false;
	List<SelectionKeyEvent> keyEventList;
	volatile List<SelectionKey> removedKey;
	public TimeoutThread(){
		// default timeout is 3 seconds
		this(3000);
	}
	public TimeoutThread(int timeout){
		this.timeout = timeout;
		this.keyEventList = new ArrayList<SelectionKeyEvent>();
	}
	
	public void addKey(SelectionKey key){
		synchronized(this.keyEventList){
			Debug.DEBUG("add timeout keyevent", 1);
			this.keyEventList.add(new SelectionKeyEvent(key, System.currentTimeMillis()));
		}
	}
	
	@Override
	public void run() {
		super.run();
		running = true;
		while(running){
			
				long timeToSleep = 0;
				synchronized(keyEventList){
					// sleep timeout milliseconds
					if(keyEventList.isEmpty()){
						timeToSleep = timeout;
					}
					else{
						// pop first event
						SelectionKeyEvent ske = keyEventList.get(0);
						
						// event has already be cancelled
						if(ske.isValid() == false){
							keyEventList.remove(0);
							continue;
						}
						// event is valid, check timestamp
						long remainingTime = ske.timestamp + timeout - System.currentTimeMillis();
						if(remainingTime > 0){
							timeToSleep = remainingTime;
						}else{
							// timeout, kill event
							//IReadWriteHandler rwh = (IReadWriteHandler)ske.key.attachment();
							keyEventList.remove(0);
							Debug.DEBUG("timeout, kill event", 1);
							this.removedKey.add(ske.key);
							Debug.DEBUG("timeout, wakeup selector", 3);
							ske.key.selector().wakeup(); // wake up the selector
							continue;
						}
					}
				}
			try {
				Thread.sleep(timeToSleep);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
			}
		}
	}
	
	public void safeStop(){
		running = false;
		// kill all events in list?
		this.interrupt();
	}
	public boolean removeKey(SelectionKey key){
		synchronized(this.keyEventList){
			for(SelectionKeyEvent ke : keyEventList){
				if(ke.key == key){
					ke.remove(); // lazy delete
					return true;
				}
			}
			return false;
		}
	}
	public void setRemovedList(List<SelectionKey> sk) {
		// TODO Auto-generated method stub
		this.removedKey = sk;
	}
}
class SelectionKeyEvent{
	SelectionKey key;
	long timestamp;
	boolean isValid;
	public SelectionKeyEvent(SelectionKey key, long timestamp){
		this.key = key;
		this.timestamp = timestamp;
		this.isValid = true;
	}
	public void remove(){
		this.isValid = false;
	}
	
	public boolean isValid(){
		return this.isValid;
	}
}
