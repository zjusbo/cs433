
include Java

Server = Java::org.xsocket.connection.Server
ConnectionUtils = Java::org.xsocket.connection.ConnectionUtils
IConnection = Java::org.xsocket.connection.IConnection
BlockingConnection = Java::org.xsocket.connection.BlockingConnection
IConnectHandler = Java::org.xsocket.connection.IConnectHandler
IDataHandler = Java::org.xsocket.connection.IDataHandler
JRubyHandlerAdapter2 = Java::org.xsocket.connection.JRubyHandlerAdapter2
NonBlockingConnection = Java::org.xsocket.connection.NonBlockingConnection



class ServerHandler
   include IConnectHandler
   include IDataHandler

   def nbc
      @nbc
   end

   def onConnect(nbc)
      @nbc = nbc
      return true
   end


   def onData(nbc)
     nbc.read_string_by_delimiter("\r\n")
     return true
   end
end

 
hdl = ServerHandler.new()
server = Server.new(JRubyHandlerAdapter2.new(hdl)) # handler adapter required to restore exceptions. See javadoc
ConnectionUtils.start(server)

client = BlockingConnection.new('localhost', server.local_port)
client.write('testdata')

sleep 1

if hdl.nbc.open?
   print 'OK'
else
   print 'buffer underflow exception caused another exception'
end

client.close()
server.close()
