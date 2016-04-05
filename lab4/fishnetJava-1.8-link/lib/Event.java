import java.lang.reflect.InvocationTargetException;

/**
 * An Event is some scheduled task to be performed.
 */
public class Event {

    private long timeToOccur;
    private Callback cb;

    /**
     * @param timeToOccur The time at which the event should take place.
     * @param cb The callback to be invoked
     */
    public Event(long timeToOccur, Callback cb) {
	this.timeToOccur = timeToOccur;
	this.cb = cb;
    }

    /**
     * When should this event occur?
     * @return The time it will happen.
     */

    public long timeToOccur(){
	return this.timeToOccur;
    }

    /**
     * Returns the callback
     * @return The callback
     */
    public Callback callback() {
	return this.cb;
    }
}
