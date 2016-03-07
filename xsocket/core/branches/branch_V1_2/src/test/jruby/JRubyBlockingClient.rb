
include Java

BlockingConnection = Java::org.xsocket.stream.BlockingConnection

bc = BlockingConnection.new('www.web.de', 80)
begin
   bc.write("GET / HTTP1/1\r\n\r\n")

   code = bc.read_string_by_delimiter("\r\n")

   if code.include? '200'
      puts 'OK'
   else
      puts "error. response code " + code
   end
ensure
   bc.close()
end
