package asyncServer;
import java.nio.channels.SelectionKey;
import java.io.IOException;

public interface IReadWriteHandler extends IChannelHandler {
    public void handleRead(SelectionKey key) throws IOException;
    public void handleWrite(SelectionKey key) throws IOException;
    public int  getInitOps();
    
    // cancel current client
    public void cancel();
	public void setTimeoutthread(ITimeoutThread timeoutThread);
}