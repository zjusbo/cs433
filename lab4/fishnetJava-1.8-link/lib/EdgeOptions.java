/**
 * <pre>
 * Class to represent the edge options: loss rate, delay and bandwidth
 * </pre>
 */
public class EdgeOptions {
    double lossRate;
    long delay;
    int bw;
    /*
     * Mar. 12, 2006
     * Hao Wang
     *
     * buffering time (in milliseconds)
     */
    long bt;

    /**
     * Initializes loss rate to 0. Lossless link by default.
     * Initializes delay to 1 millisecond and bandwidth to 10KB/s
     */
    public EdgeOptions() {
	lossRate = 0.0;
	delay = 1;
	bw = 10000;
        bt = 250;
    }

    /**
     * Sets the loss rate
     * @param lossRate The new loss rate
     */
    public void setLossRate(double lossRate) {
	this.lossRate = lossRate;
    }

    /**
     * Sets the delay
     * @param delay The new delay, in milliseconds
     */
    public void setDelay(long delay) {
	this.delay = delay;
    }

    /**
     * Sets the bandwidth
     * @param bw The new bandwidth, in B/s
     */
    public void setBW(int bw) {
	this.bw = bw;
    }

    /**
     * Returns the loss rate
     * @return The loss rate
     */
    public double getLossRate() {
	return lossRate;
    }

    /**
     * Returns the delay
     * @return The delay in milliseconds
     */
    public long getDelay() {
	return delay;
    }

    /**
     * Returns the bandwidth
     * @return The bandwidth in B/s
     */
    public int getBW() {
	return bw;
    }

    /*
     * Mar. 12, 2006
     * Hao Wang
     *
     * support the option for buffering time
     */

    /**
     * Returns the buffering time
     * @return int
     */
    public long getBT() {
        return bt;
    }

    /**
     * Sets the buffering time
     * @param bt int The new buffering time, in milliseconds
     */
    public void setBT(long bt) {
        this.bt = bt;
    }
}
