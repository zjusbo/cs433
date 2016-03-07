package org.xsocket.connection


import org.xsocket.connection.BlockingConnection

 

def bc = new BlockingConnection('www.gmx.de', 80)
 
try {
  bc.write("GET / HTTP/1.1\r\nUser-Agent: me\r\nHost: www.gmx.com\r\n\r\n")

  def response = bc.readStringByDelimiter("\r\n")
  if (response.contains('HTTP')) {
    println 'OK'
  } else {
    println 'ERROR got ' + response
  }
} finally {
  bc.close()
} 