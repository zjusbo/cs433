
include Java

BlockingConnection = Java::org.xsocket.connection.BlockingConnection


bc = BlockingConnection.new('www.web.de', 80)
begin
   bc.write("GET / HTTP/1.1\r\nUser-Agent: me\r\nHost: www.web.de\r\n\r\n")

   code = bc.read_string_by_delimiter("\r\n")

   if code.include? 'HTTP'
      puts 'OK'
   else
      puts "error. response code " + code
   end
ensure
   bc.close()
end
