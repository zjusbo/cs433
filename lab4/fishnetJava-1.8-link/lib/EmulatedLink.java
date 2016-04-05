/**
 * <p>Title: CS 433/533 Fishnet Programming Assignments</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

import java.net.DatagramPacket;
import java.io.IOException;

/**
 * <p> A class for physical link emulation </p>
 */
public class EmulatedLink {

    // link options: loss rate, delay, and bandwidth
    private EdgeOptions options;
    // When can the next packet be put onto the wire (in microseconds)
    private long nextPktSendTime;

    /**
     * Create an emulated physical link
     * @param options EdgeOptions Options for this link
     */
    public EmulatedLink(EdgeOptions options) {
        this.options = options;
        this.nextPktSendTime = 0;
    }

    /**
     * Figure out when, in microseconds, a packet should be physically send out to the destination, given a link's
     * bandwidth propogation delay characteristics.
     * @param manager The manager that is scheduling the packet
     * @param size The size of the packet in bytes
     * @param now The current time in microseconds
     * @return The time (in microseconds) when the packet should be physically send out to the destination. Returns -1 if the packet is dropped/lost
     */
    public long schedulePkt(Manager manager, int size, long now) {
        long currentPktSendTime = Math.max(now, this.nextPktSendTime);
        /*
         * Mar. 13, 2006
         * Hao Wang
         *
         * unit of bandwidth now B/s
         */
        long finishTime = currentPktSendTime + size * 1000000 / this.options.getBW();
        if (finishTime - now > this.options.getBT() * 1000) {
            // buffer overflow, drop packet
            manager.packetDropped();
            return -1;
        }
        this.nextPktSendTime = finishTime;

        if(Math.random() < this.options.getLossRate()) {
            // packet lost due to transmission error
            manager.packetLost();
            return -1;
        }

        return  finishTime + (this.options.getDelay() * 1000);
    }
}
