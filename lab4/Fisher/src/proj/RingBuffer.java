package proj;


/**
 * Thread safe ringbuffer 
 **/
public class RingBuffer {
	
	private int capacity;
	private int front, rear;
	private byte[] data;
	
	public RingBuffer(int capacity){
		data = new byte[capacity];
		front = rear = 0;
		this.capacity = capacity;
	}
	
	/**
	 * get data in range [start, start + len)
	 **/
	public synchronized byte[] get(int start, int len){
		// avaliable data length
		len = Math.min(len, front - rear - start);
		
		if(len <= 0) return new byte[0];
		
		byte[] re = new byte[len];
		int start_idx = (start + rear) % capacity;
		
		// no wrap up
		if(start_idx + len <= capacity){
			System.arraycopy(data, start_idx, re, 0, len);
		}else{
			System.arraycopy(data, start_idx, re, 0, capacity - start_idx);
			System.arraycopy(data, 0, re, capacity - start_idx, len - (capacity - start_idx));
		}
		return re;
	}
	public synchronized int capacity(){
		return capacity;
	}
	// return size of this buffer
	public synchronized int size(){
		return front - rear;
	}
	/**
	 * advance the window by offset
	 * pop elements out
	 **/
	public synchronized void advance(int offset){
		if(offset <= 0) return ;
		rear += offset;
		if(rear > front){
			rear = front;
		}
	}
	/**
	 * put data into buffer, return length that is successfully stored in buffer
	 * thread safe
	 **/
	public synchronized int put(byte[] elements){
		int len = Math.min(elements.length, remaining());
		int start_idx = front % capacity;
		// no wrap up
		if(start_idx + len <= capacity){
			System.arraycopy(elements, 0, data, start_idx, len);
		}else{
			System.arraycopy(elements, 0, data, start_idx, capacity - start_idx);
			System.arraycopy(elements, capacity - start_idx, data, 0, len - (capacity - start_idx));
		}
		front += len;
		return len;
	}
	
	/**
	 * remaining space in buffer, in bytes
	 */
	public synchronized int remaining(){
		return capacity - size();
	}
}