
include Java

MultithreadedServer = Java::org.xsocket.stream.MultithreadedServer
StreamUtils = Java::org.xsocket.stream.StreamUtils
IConnection = Java::org.xsocket.stream.IConnection
BlockingConnection = Java::org.xsocket.stream.BlockingConnection
IConnectHandler = Java::org.xsocket.stream.IConnectHandler
IDataHandler = Java::org.xsocket.stream.IDataHandler
JRubyHandlerAdapter = Java::org.xsocket.stream.JRubyHandlerAdapter
NonBlockingConnection = Java::org.xsocket.stream.NonBlockingConnection





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
server = MultithreadedServer.new(JRubyHandlerAdapter.new(hdl)) # handler adapter required to restore exceptions. See javadoc
StreamUtils.start(server)

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
