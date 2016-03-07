package asyncServer;

import java.nio.channels.SelectionKey;

public interface ITimeoutThread{
	public void  addKey(SelectionKey key);
	void safeStop();
	public boolean removeKey(SelectionKey key);
}
