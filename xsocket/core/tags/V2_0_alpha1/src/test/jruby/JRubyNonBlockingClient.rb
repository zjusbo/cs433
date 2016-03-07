
require 'java'

include_class 'org.xsocket.stream.IConnection'
include_class 'org.xsocket.stream.BlockingConnection'
include_class 'org.xsocket.stream.IConnectHandler'
include_class 'org.xsocket.stream.IDataHandler'
include_class 'org.xsocket.stream.NonBlockingConnection'
include_class 'org.xsocket.stream.JRubyHandlerAdapter'





class ClientHandler
   include IConnectHandler
   include IDataHandler

   def onConnect(nbc)
      nbc.flushmode = IConnection::FlushMode::ASYNC  # for performance reasons (only required by writes, which will not be done in this example)

      @is_handled = false
      return true
   end


   def onData(nbc)
       if not @is_handled
           code = nbc.read_string_by_delimiter("\r\n")
           @is_handled = true

           if (code.include? '200')
             puts 'OK'
           else
             puts "error. response code " + code
           end
       end
       return true
   end

end



# Caution! JRubyHandlerAdapter is required as workaround for JRuby bug.
# See JavaDoc http://xsocket.svn.sourceforge.net/viewvc/xsocket/xsocket/core/branches/branch_V1_2/src/test/java/org/xsocket/stream/JRubyHandlerAdapter.java?view=markup
# the JRubyHandlerAdapter class can be downloaded here http://xsocket.sourceforge.net/bugfix/JRubyHandlerAdapter.class
nbc = NonBlockingConnection.new('www.web.de', 80, JRubyHandlerAdapter.new(ClientHandler.new))

begin
  nbc.write("GET / HTTP1/1\r\n\r\n")

  sleep 1

ensure
  nbc.close()
end
