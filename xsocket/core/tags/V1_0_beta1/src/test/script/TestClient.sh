#!/bin/sh
java -cp ../../../bin -Djava.util.logging.config.file=../java/logging.properties  org.xsocket.server.smtp.BulkTestMailSender 127.0.0.1 7743 8