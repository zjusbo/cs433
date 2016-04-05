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
 * <p> A transfer server using Fishnet socket API </p>
 */
public class TransferServer extends FishThread {
    private TCPSock serverSock;
    private long serverInterval;
    private long workerInterval;
    private int sz;

    // default settings
    public static final long DEFAULT_SERVER_INTERVAL = 1000;
    public static final long DEFAULT_WORKER_INTERVAL = 1000;
    public static final int DEFAULT_BUFFER_SZ = 65536;

    /**
     * Create a transfer server
     *
     * @param manager Manager The Fishnet manager
     * @param node Node The node that is creating this server
     * @param serverSock TCPSock The server socket for this server
     * @param serverInterval long The execution interval of this server
     * @param workerInterval long The execution interval of accepted connections
     * @param sz int The buffer size of the worker
     */
    public TransferServer(Manager manager, Node node, TCPSock serverSock,
                          long serverInterval, long workerInterval, int sz) {
        super(manager, node);
        this.serverSock = serverSock;
        this.serverInterval = serverInterval;
        this.workerInterval = workerInterval;
        this.sz = sz;

        this.setInterval(this.serverInterval);
    }

    /**
     * Create a transfer server
     *
     * @param manager Manager The Fishnet manager
     * @param node Node The node that is creating this server
     * @param serverSock TCPSock The server socket for this server
     */
    public TransferServer(Manager manager, Node node, TCPSock serverSock) {
        this(manager, node, serverSock,
             DEFAULT_SERVER_INTERVAL,
             DEFAULT_WORKER_INTERVAL,
             DEFAULT_BUFFER_SZ);
    }

    public void execute() {
        if (!serverSock.isClosed()) {
            // try to accept an established connection
            TCPSock connSock = serverSock.accept();

            if (connSock == null) return;

            // start a worker thread to serve the new connection
            node.logOutput("time = " + manager.now() + " msec");
            node.logOutput("connection accepted");
            TransferWorker worker = new
                TransferWorker(manager, node, connSock, workerInterval, sz);
            worker.start();
        } else {
            // server socket closed, shutdown
            node.logOutput("time = " + manager.now() + " msec");
            node.logOutput("server shutdown");
            this.stop();
        }
    }

    private class TransferWorker extends FishThread {
        private TCPSock sock;
        private long interval;
        private byte[] buf;
        private int pos;

        public TransferWorker(Manager manager, Node node, TCPSock sock,
                              long interval, int sz) {
            super(manager, node);
            this.sock = sock;
            this.interval = interval;
            this.buf = new byte[sz];
            this.pos = 0;

            this.setInterval(interval);
        }

        public void execute() {
            if (!sock.isClosed()) {
                //node.logOutput("receiving...");
                int index = pos % buf.length;

                int len = buf.length - index;
                int count = sock.read(buf, index, len);

                if (count == -1) {
                    // on error, release the socket immediately
                    node.logError("time = " + manager.now() + " msec");
                    node.logError("receiving aborted");
                    node.logError("position = " + pos);
                    node.logError("releasing connection...");
                    sock.release();
                    this.stop();
                    return;
                }

                if (count > 0) {
                    //node.logOutput("verifying data...");
                    for (int i = index; i < index + count; i++) {
                        if (buf[i] != (byte) i) {
                            // data corrupted
                            node.logError("time = " + manager.now() + " msec");
                            node.logError("data corruption detected");
                            node.logError("position = " + pos);
                            node.logError("releasing connection...");
                            sock.release();
                            this.stop();
                            return;
                        }
                    }
                }

                pos += count;

                //node.logOutput("time = " + manager.now() + " msec");
                //node.logOutput("bytes received = " + count);
                return;
            }

            node.logOutput("time = " + manager.now() + " msec");
            node.logOutput("connection closed");
            node.logOutput("total bytes received = " + pos);
            this.stop();
        }
    }
}
