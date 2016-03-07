package org.xsocket.connection

import org.xsocket.connection.Server
import org.xsocket.connection.IConnection
import org.xsocket.connection.NonBlockingConnection
import org.xsocket.connection.IConnectHandler
import org.xsocket.connection.IDisconnectHandler
import org.xsocket.connection.IDataHandler


 
class Handler implements IConnectHandler, IDisconnectHandler {
	def timer = new Timer(true)

	def boolean onConnect(INonBlockingConnection nbc) throws IOException {
		def notifier = new Notifier(nbc)
		timer.schedule(notifier, 500, 500)
		
		return true
	}
	
	def boolean onDisconnect(INonBlockingConnection nbc) throws IOException {
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


def server = new Server(0, new Handler())
ConnectionUtils.start(server)

def con = new BlockingConnection("localhost", server.getLocalPort());

3.times { 
	def serverMsg = con.readStringByDelimiter("\r\n")
}

con.close()
server.close()

println 'OK'

