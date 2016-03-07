package org.xsocket.web.http;

import java.io.Closeable;
import java.io.Flushable;
import java.nio.channels.WritableByteChannel;

import org.xsocket.IDataSink;



public interface IWriteableChannel extends IDataSink, WritableByteChannel, Closeable, Flushable {
	

}
