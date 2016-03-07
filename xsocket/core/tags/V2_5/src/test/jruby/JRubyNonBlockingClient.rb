
include Java

IConnection = Java::org.xsocket.connection.IConnection
BlockingConnection = Java::org.xsocket.connection.BlockingConnection
IConnectHandler = Java::org.xsocket.connection.IConnectHandler
IDataHandler = Java::org.xsocket.connection.IDataHandler
NonBlockingConnection = Java::org.xsocket.connection.NonBlockingConnection
JRubyHandlerAdapter2 = Java::org.xsocket.connection.JRubyHandlerAdapter2






class ClientHandler
   include IConnectHandler
   include IDataHandler

   def onConnect(nbc)
      @is_handled = false
      return true
   end


   def onData(nbc)
       if not @is_handled
           code = nbc.read_string_by_delimiter("\r\n")
           @is_handled = true

           if (code.include? '200')
             puts 'HTTP'
           else
             puts "error. response code " + code
           end
       end
       return true
   end

end


hdl = ClientHandler.new
hdl = JRubyHandlerAdapter2.new(hdl) # handler adapter required to restore exceptions. See javadoc

nbc = NonBlockingConnection.new('www.gmx.com', 80, hdl)

nbc.write("GET / HTTP/1.1\r\nUser-Agent: me\r\nHost: www.gmx.com\r\n\r\n")

sleep 1

nbc.close()
