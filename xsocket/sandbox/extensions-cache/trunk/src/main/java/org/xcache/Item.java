// $Id: DataConverter.java 1546 2007-07-23 06:07:56Z grro $

/*
 *  Copyright (c) xcache.org, 2007. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xcache.org/
 */
package org.xcache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;


import org.xsocket.IDataSink;
import org.xsocket.IDataSource;



/**
 * 
 * 
 * @author grro@xcache.org
 */
final class Item {
	
	private static final Logger LOG = Logger.getLogger(Item.class.getName());
	
	private static final int PACK_THRESHOLD = 16; 

	private static final byte PLAIN = 90;
	private static final byte PACKED = 91;
	
	private static final String ENCODING = "UTF-8";
	
	private static final byte TYPE_NULL = 0;
	private static final byte TYPE_STRING = 1;
	private static final byte TYPE_BYTEARRAY = 6;
	private static final byte TYPE_SERIALIZED_JAVA_OBJECT = 11;
	

	private boolean isResolved = true;
	private Integer valueLength = null;
	
	private Object value = null;
	private byte type = TYPE_STRING;

	
	
	Item(Object value) {
		if (value == null) {
			setValueWithNull();
			return;
		} 
		
		if (value instanceof String) {
			setValue((String) value);
			
		} if (value instanceof byte[]) {
			setValue((byte[]) value);
			
		} else {
			setValue((Serializable) value);
		}
	}

	

	private Item(Object value, byte type) {
		this.value = value;
		this.type = type;
	}


	private Item(ByteBuffer[] value, byte type, Integer valueLength) {
		isResolved = false;
		this.valueLength = valueLength;
		
		this.value = value;
		this.type = type;
	}

	
	private void setValueWithNull() {
		this.type = TYPE_NULL;
	}
	
	
	private void setValue(String value) {
		this.value = value;
		this.type = TYPE_STRING;
	}
	
	
	private void setValue(byte[] value) {
		this.value = value;
		this.type = TYPE_BYTEARRAY;
	}
	

	private void setValue(Serializable value) {
		this.value = value;
		this.type = TYPE_SERIALIZED_JAVA_OBJECT;
	}

	
	
	Object getValue() {
		return value;
	}

	
	
	int writeTo(IDataSink dataSink) throws IOException {
		int written = 0;
		
		written += dataSink.write(type);
		
		// value is resolved
		if (isResolved) {
			switch (type){
			
			case TYPE_NULL:
				break;
			
			case TYPE_STRING:
				byte[] serializedString = ((String) value).getBytes(ENCODING);
				written += writeItem(dataSink, serializedString);
				break;
				
			case TYPE_SERIALIZED_JAVA_OBJECT:
				byte[] serializedData = Serializer.serialize((Serializable) value);
				written += writeItem(dataSink, serializedData);
				break;
	
			default:
				throw new IOException("unknown data type " + type); 
			}
			
		// unresolved  8value contains packed field & data)
		} else {
			ByteBuffer[] bufs = (ByteBuffer[]) value; 
			ByteBuffer[] copy = new ByteBuffer[bufs.length];
			for (int i = 0; i < bufs.length; i++) {
				copy[i] = bufs[i].duplicate();
			}
			
			written += dataSink.write((int) valueLength);
			written += dataSink.write((ByteBuffer[]) copy);
		}
			
		return written;
	}

	
	private int writeItem(IDataSink dataSink, byte[] data) throws IOException {
		
		int written = 0;
		
		
		if (data.length > PACK_THRESHOLD) {
			byte[] compressedData = compress(data);
			
			if (compressedData.length < data.length) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("item compressed (compressed size=" + (compressedData.length * 100 / data.length) + "%)");
				}
				
				written += dataSink.write(compressedData.length);
				written += dataSink.write(PACKED);
				written += dataSink.write(compressedData);
				
				return written;
			} 
		}
			

		// plain
		written += dataSink.write(data.length);
		written += dataSink.write(PLAIN);
		written += dataSink.write(data);
		
		return written;
	}
	
	
	private static byte[] compress(byte[] plainData) {
	    Deflater compressor = new Deflater();
	    compressor.setLevel(Deflater.BEST_COMPRESSION);
	    
	    compressor.setInput(plainData);
	    compressor.finish();
	    
	    ByteArrayOutputStream bos = new ByteArrayOutputStream(plainData.length);
	    
	    byte[] buf = new byte[1024];
	    while (!compressor.finished()) {
	        int count = compressor.deflate(buf);
	        bos.write(buf, 0, count);
	    }

	    try {
	        bos.close();
	    } catch (IOException e) { }
	    
	    return bos.toByteArray();
	}
	
	private static byte[] decompress(byte[] compressedData) {
	    Inflater decompressor = new Inflater();
	    decompressor.setInput(compressedData);
	    
	    ByteArrayOutputStream bos = new ByteArrayOutputStream(compressedData.length);
	    
	    // Decompress the data
	    byte[] buf = new byte[1024];
	    while (!decompressor.finished()) {
	        try {
	            int count = decompressor.inflate(buf);
	            bos.write(buf, 0, count);
	        } catch (DataFormatException e) { }
	    }
	    
	    try {
	        bos.close();
	    } catch (IOException e) { }

	    
	    byte[] data = bos.toByteArray();
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("item decompressed (compressed size=" + (compressedData.length * 100 / data.length) + "%)");
		}

	    
	   return data;
	}
	

	public static Item readFrom(IDataSource dataSource) throws IOException {
		
		Object value = null;
		byte type = dataSource.readByte();
		
		int length = dataSource.readInt();
		boolean isPacked = (dataSource.readByte() == PACKED);

		
		switch (type) {
		
		case TYPE_NULL:
			break;
		
		case TYPE_STRING:
			if (isPacked) {
				byte[] data = decompress(dataSource.readBytesByLength(length));
				value = new String(data, ENCODING);
			} else {
				value = dataSource.readStringByLength(length, ENCODING);
			}
			break;
			
		case TYPE_SERIALIZED_JAVA_OBJECT:
			byte[] data = null;
			if (isPacked) {
				data = decompress(dataSource.readBytesByLength(length));
			} else {
				data = dataSource.readBytesByLength(length);
			}
			value = Serializer.deserialize(data);
			break;

		default:
			throw new IOException("unknown data type " + type); 
		}
		
		return new Item(value, type);
	}

	
	
	public static Item readUnresolvedFrom(IDataSource dataSource) throws IOException {

		byte type = dataSource.readByte();
		
		int length = dataSource.readInt();
		ByteBuffer[] buf = dataSource.readByteBufferByLength(length + 1);
		
		Item item = new Item(buf, type, length);
		return item;
	}

	
	@Override
	public int hashCode() {
		return value.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		
		if (!(other instanceof Item)) {
			return false;
		}
		
		Item otherItem = (Item) other;
		return ((otherItem.type == this.type) && (otherItem.value.equals(this.value))); 
	}

	
	
	@Override
	public String toString() {
		return "[" + type + "] " + value;
	}
	
	private static final class Serializer {
		
		static byte[] serialize(Serializable obj) throws IOException {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			new ObjectOutputStream(os).writeObject(obj);
			
			return os.toByteArray(); 
		}
		
		static Serializable deserialize(byte[] serialized) throws IOException {
			try {
				ByteArrayInputStream is = new ByteArrayInputStream(serialized);
				ObjectInputStream ois = new ObjectInputStream(is);
				
				return (Serializable) ois.readObject();
			} catch (ClassNotFoundException cnf) {
				throw new IOException(cnf.toString());
			}
		}
		
	}
}
