#!/bin/sh
java -cp ../bin -Djava.util.logging.config.file=../config/logging.properties org.xsocket.server.SmtpTestClient 127.0.0.1 7743 8