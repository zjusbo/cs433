/*
 * Apr. 1, 2006
 * Hao Wang
 *
 * Eliminate the use of a polling thread to improve performance
 */

import java.util.LinkedList;

public class MultiplexIO {

    private LinkedList data;  // in 1.5, was private LinkedList<Integer> data;

    public MultiplexIO() {
	this.data = new LinkedList();
    }

    public synchronized void write(int b) {
        this.data.add(new Integer(b));
        this.notifyAll();
    }

    public synchronized int read() {
	if(!this.isEmpty()) {
	    return ((Integer) this.data.removeFirst()).intValue();
	    // in 1.5 was return this.data.poll().intValue();
	}
	return -1;
    }

    public synchronized boolean isEmpty() {
	return this.data.isEmpty();
    }
}
