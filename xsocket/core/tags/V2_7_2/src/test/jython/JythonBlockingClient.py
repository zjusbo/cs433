
from org.xsocket.connection import BlockingConnection



bc = BlockingConnection('www.gmx.de', 80)
try:
   bc.write('GET / HTTP/1.1\r\nUser-Agent: me\r\nHost: www.gmx.com\r\n\r\n')
   response = bc.readStringByDelimiter('\r\n')

   if 'HTTP' in response: 
      print 'OK'
   else:
      print 'ERROR got ' + response

finally:
   bc.close()	   
 