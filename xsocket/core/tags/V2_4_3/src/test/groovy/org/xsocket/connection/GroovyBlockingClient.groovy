package org.xsocket.connection


import org.xsocket.connection.BlockingConnection

 

def bc = new BlockingConnection('www.gmx.de', 80)
 
try {
  bc.write("GET / HTTP1/1 \r\n\r\n")

  def response = bc.readStringByDelimiter("\r\n")
  if (response.contains('200')) {
    println 'OK'
  } else {
    println 'ERROR got ' + response
  }
} finally {
  bc.close()
} 