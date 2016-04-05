/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

import java.lang.reflect.Method;

/**
 * <p> A utility class for multi-threading in Fishnet </p>
 */
public class FishThread {
    protected Manager manager;
    protected Node node;
    protected int addr;

    /**
     * Task execution interval, in milliseconds, 0 means no more execution
     */
    private long interval;

    /**
     * Default task execution interval (1 second)
     */
    public static final long DEFAULT_INTERVAL = 1000;

    /**
     * The callback used to schedule task execution
     */
    private Callback cb;

    /**
     * Create a thread in a Fishnet node
     *
     * @param manager Manager The Fishnet manager
     * @param node Node The node that is creating this thread
     * @param interval long The task execution interval
     */
    public FishThread(Manager manager, Node node, long interval) {
        this.manager = manager;
        this.node = node;
        this.addr = node.getAddr();
        this.interval = interval;

        try {
            Method method = Callback.getMethod("run", this, null);
            this.cb = new Callback(method, this, null);
        } catch (Exception e) {
            // This should not happen
            this.node.logError("failed to initialize a FishThread");
            System.exit(1);
        }
    }

    /**
     * Create a thread in a Fishnet node with default task execution interval
     *
     * @param manager Manager The Fishnet manager
     * @param node Node The node that is creating this thread
     */
    public FishThread(Manager manager, Node node) {
        this(manager, node, DEFAULT_INTERVAL);
    }

    /**
     * Set the task execution interval of this thread
     *
     * @param interval long The new task execution interval
     */
    public void setInterval(long interval) {
        if (interval > 0) {
            this.interval = interval;
        }
    }

    /**
     * Start this thread
     */
    public void start() {
        schedule();
    }

    /**
     * Start this thread and execute the task now
     */
    public void startNow() {
        execute();
        schedule();
    }

    /**
     * The task that is peroidically executed by this thread. This method does
     * nothing and returns. Subclasses should override this method to perform
     * their tasks.
     */
    public void execute() {

    }

    /**
     * Stop this thread. No more execution will be scheduled.
     */
    public void stop() {
        this.interval = 0;
    }

    /**
     * The callback used to execute the task
     */
    public final void run() {
        execute();
        schedule();
    }

    /**
     * Schedule next callback if necessary
     */
    private void schedule() {
        // no more execution if interval <= 0
        if (this.interval <= 0) return;

        this.manager.addTimer(this.addr, this.interval, this.cb);
    }
}
