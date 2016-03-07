package org.xsocket.stream

import org.xsocket.stream.MultithreadedServer
import org.xsocket.stream.IConnection
import org.xsocket.stream.NonBlockingConnection
import org.xsocket.stream.IConnectHandler
import org.xsocket.stream.IDisconnectHandler
import org.xsocket.stream.IDataHandler

 
 
class Handler implements IConnectHandler, IDisconnectHandler {
	def timer = new Timer(true)

	def boolean onConnect(INonBlockingConnection connection) throws IOException {
		def notifier = new Notifier(connection)
		timer.schedule(notifier, 500, 500)
		
		return true
	}
	
	def boolean onDisconnect(INonBlockingConnection connection) throws IOException {
		timer.cancel()
		
		return true
	}
}



class Notifier extends TimerTask {
	def INonBlockingConnection connection
	
	def Notifier(connection) {
		this.connection = connection
	}
	
	
	public void run() {
		connection.write("pong\r\n")
	}
}	


def server = new MultithreadedServer(0, new Handler())
StreamUtils.start(server)

def con = new BlockingConnection("localhost", server.getLocalPort());

3.times { 
	def serverMsg = con.readStringByDelimiter("\r\n")
}

con.close()
server.close()

println 'OK'

