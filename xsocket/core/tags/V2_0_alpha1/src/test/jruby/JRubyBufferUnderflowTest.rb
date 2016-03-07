
require 'java'


include_class 'org.xsocket.stream.Server'
include_class 'org.xsocket.stream.StreamUtils'
include_class 'org.xsocket.stream.IConnection'
include_class 'org.xsocket.stream.BlockingConnection'
include_class 'org.xsocket.stream.IConnectHandler'
include_class 'org.xsocket.stream.IDataHandler'
include_class 'org.xsocket.stream.JRubyHandlerAdapter'
include_class 'org.xsocket.stream.NonBlockingConnection'






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
server = Server.new(JRubyHandlerAdapter.new(hdl)) # handler adapter required to restore exceptions. See javadoc
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
