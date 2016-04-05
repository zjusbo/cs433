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

/**
 * <p> A transfer client using Fishnet socket API </p>
 */
public class TransferClient extends FishThread {
    private TCPSock sock;
    private long interval;
    private byte[] buf;

    public static final long DEFAULT_CLIENT_INTERVAL = 1000;
    public static final int DEFAULT_BUFFER_SZ = 65536;

    // number of bytes to send
    private int amount;
    // starting and finishing time in milliseconds
    private long startTime;
    private long finishTime;
    private int pos;

    public TransferClient(Manager manager, Node node, TCPSock sock, int amount,
                          long interval, int sz) {
        super(manager, node);
        this.sock = sock;
        this.interval = interval;
        this.buf = new byte[sz];
        this.amount = amount;
        this.startTime = 0;
        this.finishTime = 0;
        this.pos = 0;

        this.setInterval(this.interval);
    }

    public TransferClient(Manager manager, Node node, TCPSock sock, int amount) {
        this(manager, node, sock, amount,
             DEFAULT_CLIENT_INTERVAL,
             DEFAULT_BUFFER_SZ);
    }

    public void execute() {
        if (sock.isConnectionPending()) {
            //node.logOutput("connecting...");
            return;
        } else if (sock.isConnected()) {

            if (startTime == 0) {
                // record starting time
                startTime = manager.now();
                node.logOutput("time = " + startTime + " msec");
                node.logOutput("started");
                node.logOutput("bytes to send = " + amount);
            }

            if (amount == 0) {
                // sending completed, initiate closure of connection
                node.logOutput("time = " + manager.now());
                node.logOutput("sending completed");
                node.logOutput("closing connection...");
                sock.close();
                return;
            }

            //node.logOutput("sending...");
            int index = pos % buf.length;

            if (index == 0) {
                // generate new data
                for (int i = 0; i < buf.length; i++) {
                    buf[i] = (byte) i;
                }
            }

            int len = Math.min(buf.length - index, amount);
            int count = sock.write(buf, index, len);

            if (count == -1) {
                // on error, release the socket immediately
                node.logError("time = " + manager.now() + " msec");
                node.logError("sending aborted");
                node.logError("position = " + pos);
                node.logError("releasing connection...");
                sock.release();
                this.stop();
                return;
            }

            pos += count;
            amount -= count;

            //node.logOutput("time = " + manager.now());
            //node.logOutput("bytes sent = " + count);
            return;
        } else if (sock.isClosurePending()) {
            //node.logOutput("closing connection...");
            return;
        } else if (sock.isClosed()) {
            finishTime = manager.now();
            node.logOutput("time = " + manager.now() + " msec");
            node.logOutput("connection closed");
            node.logOutput("total bytes sent = " + pos);
            node.logOutput("time elapsed = " +
                           (finishTime - startTime) + " msec");
            node.logOutput("Bps = " + pos * 1000.0 / (finishTime - startTime));
            // release the socket
            sock.release();
            this.stop();
            return;
        }

        node.logError("shouldn't reach here");
        System.exit(1);
    }
}
