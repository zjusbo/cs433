package asyncServer;
import java.nio.channels.SelectionKey;
import java.io.IOException;

public interface IAcceptHandler extends IChannelHandler {
    public void handleAccept(SelectionKey key) throws IOException;
}