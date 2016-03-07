package org.xsocket.group;

import java.net.InetSocketAddress;

import org.xsocket.ILifeCycle;

public interface IGroupNodeListener extends ILifeCycle {

	public void onMemberJoined(InetSocketAddress address);
	
	public void onMemberLeaved(InetSocketAddress address);
	
	public void onMemberTimeout(InetSocketAddress address);
}

