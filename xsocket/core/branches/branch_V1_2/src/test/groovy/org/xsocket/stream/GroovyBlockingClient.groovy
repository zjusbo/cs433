package org.xsocket.stream


import org.xsocket.stream.BlockingConnection



def bc = new BlockingConnection('www.web.de', 80)

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