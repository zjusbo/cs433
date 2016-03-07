
from org.xsocket.stream import BlockingConnection


bc = BlockingConnection('www.web.de', 80)
try:
   bc.write('GET / HTTP1/1 \r\n\r\n')
   response = bc.readStringByDelimiter('\r\n')

   if 'OK' in response: 
      print 'OK'
   else:
      print 'ERROR got ' + response

finally:
   bc.close()	   
 