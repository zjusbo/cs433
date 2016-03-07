

include Java

MultithreadedServer = Java::org.xsocket.stream.MultithreadedServer
StreamUtils = Java::org.xsocket.stream.StreamUtils
IConnection = Java::org.xsocket.stream.IConnection
BlockingConnection = Java::org.xsocket.stream.BlockingConnection
HandlerChain = Java::org.xsocket.stream.HandlerChain
IConnectHandler = Java::org.xsocket.stream.IConnectHandler
IDataHandler = Java::org.xsocket.stream.IDataHandler
JRubyHandlerAdapter = Java::org.xsocket.stream.JRubyHandlerAdapter
NonBlockingConnection = Java::org.xsocket.stream.NonBlockingConnection






class EchoHandler
   include IConnectHandler
   include IDataHandler

   def onConnect(nbc)
      nbc.flushmode = IConnection::FlushMode::ASYNC
      return true
   end


   def onData(nbc)
     data = nbc.read_available()
     nbc.write(data)
     return true
   end
end



class FirstVisitThrottlingFilter
   include IConnectHandler

  def initialize(write_rate)
    @write_rate = write_rate
    @known_ips = Array.new
  end

   def onConnect(nbc)
    nbc.flushmode = IConnection::FlushMode::ASYNC

    ip_address = nbc.remote_address.host_address
    if not @known_ips.include?(ip_address)
       @known_ips = @known_ips << ip_address
       nbc.write_transfer_rate = @write_rate
    end

    return false  # false -> successor element in handler chain will be called (true -> chain processing will be terminated)
   end
end








hdl = EchoHandler.new()


# uncomment following code for using the first visit throttling filter
#first_visit_filter = FirstVisitThrottlingFilter.new(5)
#chain = HandlerChain.new
#chain.addLast(first_visit_filter)
#chain.addLast(hdl)
#hdl = chain


# Caution! JRubyHandlerAdapter is required as workaround for JRuby bug.
# See JavaDoc http://xsocket.svn.sourceforge.net/viewvc/xsocket/xsocket/core/branches/branch_V1_2/src/test/java/org/xsocket/stream/JRubyHandlerAdapter.java?view=markup
# the JRubyHandlerAdapter class can be downloaded here http://xsocket.sourceforge.net/bugfix/JRubyHandlerAdapter.class
server = MultithreadedServer.new(JRubyHandlerAdapter.new(hdl))
StreamUtils.start(server)


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

