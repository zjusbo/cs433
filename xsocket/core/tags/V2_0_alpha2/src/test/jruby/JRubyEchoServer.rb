

include Java

Server = Java::org.xsocket.connection.Server
ConnectionUtils = Java::org.xsocket.connection.ConnectionUtils
IConnection = Java::org.xsocket.connection.IConnection
BlockingConnection = Java::org.xsocket.connection.BlockingConnection
IConnectHandler = Java::org.xsocket.connection.IConnectHandler
IDataHandler = Java::org.xsocket.connection.IDataHandler
JRubyHandlerAdapter2 = Java::org.xsocket.connection.JRubyHandlerAdapter2
NonBlockingConnection = Java::org.xsocket.connection.NonBlockingConnection





class EchoHandler
   include IConnectHandler
   include IDataHandler

   def onConnect(nbc)
      nbc.flushmode = IConnection::FlushMode::ASYNC
      return true
   end


   def onData(nbc)
     data = nbc.read_available_byte_buffer()
     nbc.write(data)
     return true
   end
end

 


hdl = EchoHandler.new()
hdl = JRubyHandlerAdapter2.new(hdl)  # handler adapter required to restore exceptions. See javadoc
server = Server.new(hdl)
ConnectionUtils.start(server)


# check if the echo server works
bc = BlockingConnection.new('localhost', server.local_port)

request = "testdata"
bc.write(request + "\r\n")

response = bc.read_string_by_delimiter("\r\n")

if request = response
  puts 'OK'
else
  puts "error. request " + request + " is not equals to response " + response
end



bc.close()
server.close()

