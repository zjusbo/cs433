
require 'java'

include_class 'org.xsocket.stream.MultithreadedServer'
include_class 'org.xsocket.stream.StreamUtils'
include_class 'org.xsocket.stream.IConnection'
include_class 'org.xsocket.stream.BlockingConnection'
include_class 'org.xsocket.stream.HandlerChain'
include_class 'org.xsocket.stream.IConnectHandler'
include_class 'org.xsocket.stream.IDisconnectHandler'
include_class 'org.xsocket.stream.IDataHandler'
include_class 'org.xsocket.stream.JRubyHandlerAdapter'
include_class 'org.xsocket.stream.NonBlockingConnection'






class ProxyHandler
   include IConnectHandler
   include IDisconnectHandler
   include IDataHandler

   def onConnect(nbc)
      nbc.flushmode = IConnection::FlushMode::ASYNC
      return true
   end

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
       super

       proxy_to_server_connection = NonBlockingConnection.new(@forward_host, @forward_port, ProxyHandler.new)
       client_to_proxy_connection.attachment = proxy_to_server_connection
       proxy_to_server_connection.attachment = client_to_proxy_connection

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


