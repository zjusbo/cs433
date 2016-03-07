//$Id: AbstractChannelBasedEndpoint.java 1049 2007-03-21 16:42:48Z grro $
/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket.group;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

import org.xsocket.DataConverter;



/**
 *   
 *  
 * @author grro@xsocket.org
 */
public final class ObjectMessage<T extends Serializable> extends Message {

	
	ObjectMessage(Address sourceAddress, int sourceAddressSuffix, T object) throws IOException {
		super(sourceAddress, sourceAddressSuffix, ObjectMessage.class.getName(), new ByteBuffer[] {serialize(object)});
	}

	ObjectMessage() throws IOException {

	}
	
	@SuppressWarnings("unchecked")
	public T getObject() throws IOException {
		return (T) deserialize(getData());
	}
	
	
	
	private static ByteBuffer serialize(Serializable data) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		new ObjectOutputStream(os).writeObject(data);
		
		return ByteBuffer.wrap(os.toByteArray());
	}
	
	private static Serializable deserialize(ByteBuffer[] data) throws IOException {
		try {
			byte[] bytes = DataConverter.toBytes(data); 
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
			return (Serializable) ois.readObject();
		} catch (ClassNotFoundException cnf) {
			throw new RuntimeException(cnf.toString(), cnf);
		}
	}
}
