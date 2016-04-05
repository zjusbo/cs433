import java.util.LinkedList;
import java.util.ListIterator;
import java.lang.IndexOutOfBoundsException;

/**
 * <pre>   
 * This is a list of Events which should kept sorted by the time at which they are to be invoked.
 * </pre>   
 */
public class SortedEventQueue {

    private LinkedList eventList;

    /**
     * Create a new empty event queue.
     */
    public SortedEventQueue() {
	this.eventList = new LinkedList();
    }

    /**
     * Add an event to the queue in a sorted manner.
     * @param event The event to add to the queue.
     */
    public void addEvent(Event event) {
	ListIterator sortedIterator = eventList.listIterator();

	while (sortedIterator.hasNext()) {
	    if (event.timeToOccur() < ((Event)(sortedIterator.next())).timeToOccur()) {
		this.eventList.add(sortedIterator.previousIndex(), event);		
		return;
	    }
	}
	//If you've gotten to the end of the list, add it there.
	this.eventList.add(event); 
    }

    /**
     * Return the next Event to happen without removing it from the queue.
     * @return The next Event to happen. Returns null if the queue is empty
     */
    public Event getNextEvent() {
	if(this.isEmpty()) {
	    return null;
	}
	return (Event)(this.eventList.getFirst());
    }

    /**
     * Remove the next Event to happen and remove it from the queue.
     * @return The next Event to happen. Returns null if the queue is empty
     */
    public Event removeNextEvent() {
	if(this.isEmpty()) {
	    return null;
	}
	return (Event)(this.eventList.removeFirst());
    }
    
    /**
     * Checks if the event queue is empty
     * @return True if the event queue is empty
     */
    public boolean isEmpty() {
	return (this.eventList.size() == 0);
    }
}
