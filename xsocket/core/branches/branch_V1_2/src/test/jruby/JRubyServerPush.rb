
require 'thread'

include Java

MultithreadedServer = Java::org.xsocket.stream.MultithreadedServer
StreamUtils = Java::org.xsocket.stream.StreamUtils
IConnection = Java::org.xsocket.stream.IConnection
BlockingConnection = Java::org.xsocket.stream.BlockingConnection
HandlerChain = Java::org.xsocket.stream.HandlerChain
IConnectHandler = Java::org.xsocket.stream.IConnectHandler
IDisconnectHandler = Java::org.xsocket.stream.IDisconnectHandler
JRubyHandlerAdapter = Java::org.xsocket.stream.JRubyHandlerAdapter
NonBlockingConnection = Java::org.xsocket.stream.NonBlockingConnection





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



# Caution! JRubyHandlerAdapter is required as workaround for JRuby bug.
# See JavaDoc http://xsocket.svn.sourceforge.net/viewvc/xsocket/xsocket/core/branches/branch_V1_2/src/test/java/org/xsocket/stream/JRubyHandlerAdapter.java?view=markup
# the JRubyHandlerAdapter class can be downloaded here http://xsocket.sourceforge.net/bugfix/JRubyHandlerAdapter.class
server = MultithreadedServer.new(0, JRubyHandlerAdapter.new(Handler.new()))
StreamUtils.start(server)


bc = BlockingConnection.new('localhost', server.local_port)

3.times do
    serverMsg = bc.read_string_by_delimiter("\r\n")
end

bc.close()
server.close()

puts 'OK'

