
require 'thread'



include Java

Server = Java::org.xsocket.connection.Server
ConnectionUtils = Java::org.xsocket.connection.ConnectionUtils
IConnection = Java::org.xsocket.connection.IConnection
BlockingConnection = Java::org.xsocket.connection.BlockingConnection
IConnectHandler = Java::org.xsocket.connection.IConnectHandler
IDisconnectHandler = Java::org.xsocket.connection.IDisconnectHandler
NonBlockingConnection = Java::org.xsocket.connection.NonBlockingConnection
JRubyHandlerAdapter2 = Java::org.xsocket.connection.JRubyHandlerAdapter2




class Handler
   include IConnectHandler

   
   def onConnect(nbc)
      notifier = Notifier.new(nbc)
      @t = Thread.new do 
          while true do 
            notifier.run()
            sleep 1
          end
      end 
      return true   
   end
   
end



class Notifier

   def initialize(nbc)
      @nbc = nbc   
   end
   
   def run() 
      @nbc.write("pong\r\n")
   end
end


hdl = Handler.new();
hdl = JRubyHandlerAdapter2.new(hdl)  # handler adapter required to restore exceptions. See javadoc
server = Server.new(0, hdl)
ConnectionUtils.start(server)


bc = BlockingConnection.new('localhost', server.local_port)

3.times do
    serverMsg = bc.read_string_by_delimiter("\r\n")
end

bc.close()
server.close()

puts 'OK'

