

include Java

MultithreadedServer = Java::org.xsocket.stream.MultithreadedServer
StreamUtils = Java::org.xsocket.stream.StreamUtils
IConnection = Java::org.xsocket.stream.IConnection
BlockingConnection = Java::org.xsocket.stream.BlockingConnection
HandlerChain = Java::org.xsocket.stream.HandlerChain
IConnectHandler = Java::org.xsocket.stream.IConnectHandler
IDisconnectHandler = Java::org.xsocket.stream.IDisconnectHandler
IDataHandler = Java::org.xsocket.stream.IDataHandler
JRubyHandlerAdapter = Java::org.xsocket.stream.JRubyHandlerAdapter
NonBlockingConnection = Java::org.xsocket.stream.NonBlockingConnection




class ProxyHandler
   include IDisconnectHandler
   include IDataHandler


   def onDisconnect(nbc)
      if not nbc.attachment.nil?:
         nbc.attachment.close()
         nbc.attachment = None
      end
      return true
   end


   def onData(nbc)
       data = nbc.read_available()
       nbc.attachment.write(data)
       return true
   end
end



class ClientToProxyHandler < ProxyHandler
   include IConnectHandler
   include IDisconnectHandler
   include IDataHandler

    def initialize(forward_host, forward_port)
       @forward_host = forward_host
       @forward_port = forward_port
    end


    def onConnect(client_to_proxy_connection)
       client_to_proxy_connection.flushmode = IConnection::FlushMode::ASYNC

       proxy_to_server_connection = NonBlockingConnection.new(@forward_host, @forward_port, ProxyHandler.new)
       proxy_to_server_connection.flushmode = IConnection::FlushMode::ASYNC
       proxy_to_server_connection.attachment = client_to_proxy_connection

       client_to_proxy_connection.attachment = proxy_to_server_connection	
       return true
    end
end




# Caution! JRubyHandlerAdapter is required as workaround for JRuby bug.
# See JavaDoc http://xsocket.svn.sourceforge.net/viewvc/xsocket/xsocket/core/branches/branch_V1_2/src/test/java/org/xsocket/stream/JRubyHandlerAdapter.java?view=markup
# the JRubyHandlerAdapter class can be downloaded here http://xsocket.sourceforge.net/bugfix/JRubyHandlerAdapter.class
server = MultithreadedServer.new(9991, JRubyHandlerAdapter.new(ClientToProxyHandler.new("www.web.de", 80)))
StreamUtils.start(server)

bc = BlockingConnection.new('localhost', server.local_port)
begin
   bc.write("GET / HTTP1/1\r\n\r\n")

   code = bc.read_string_by_delimiter("\r\n")

   if code.include? '200'
      puts 'OK'
   else
      puts "error. responsecode " + code
   end
ensure
   bc.close()
   server.close()
end


