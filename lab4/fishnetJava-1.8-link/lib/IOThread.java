import java.lang.Thread;
import java.util.LinkedList;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class IOThread extends Thread {

    LinkedList inputLines;   // in 1.5, was LinkedList<String> inputLines;
    BufferedReader reader;

    public IOThread() {
	this.inputLines = new LinkedList();
	this.reader = new BufferedReader(new InputStreamReader(System.in));
    }

    public void run() {
	while(true) {
	    try {		
		this.addLine(this.reader.readLine());
	    }catch(Exception e) {
		System.err.println("Exception while waiting for user input. Exception: " + e);
	    }
	}
    }

    public synchronized boolean isEmpty() {
	return this.inputLines.isEmpty();
    }

    public synchronized String readLine() {
	if (this.inputLines.size() > 0) {
	    return (String) this.inputLines.removeFirst();
	} else {
	    return null;
	}
	// in 1.5, this if/else block was return this.inputLines.poll();
    }

    protected synchronized void addLine(String line) {
	this.inputLines.add(line);
	this.notifyAll();
    }
}
