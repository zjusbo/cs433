


include Java

Server = Java::org.xsocket.connection.Server
ConnectionUtils = Java::org.xsocket.connection.ConnectionUtils
IConnection = Java::org.xsocket.connection.IConnection
BlockingConnection = Java::org.xsocket.connection.BlockingConnection
IConnectHandler = Java::org.xsocket.connection.IConnectHandler
IDisconnectHandler = Java::org.xsocket.connection.IDisconnectHandler
IDataHandler = Java::org.xsocket.connection.IDataHandler
JRubyHandlerAdapter2 = Java::org.xsocket.connection.JRubyHandlerAdapter2
NonBlockingConnection = Java::org.xsocket.connection.NonBlockingConnection




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
       data = nbc.read_byte_buffer_by_length(nbc.available())
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


hdl = ClientToProxyHandler.new("www.web.de", 80)
hdl = JRubyHandlerAdapter2.new(hdl)  # handler adapter required to restore exceptions. See javadoc

server = Server.new(9988, hdl)
ConnectionUtils.start(server)

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


