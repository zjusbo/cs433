// $Id: AbstractGetCommand.java 335 2006-10-16 06:10:05Z grro $

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
package org.xsocket.mail.pop3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.xsocket.mail.DummyMail;
import org.xsocket.mail.pop3.spi.IPOP3Maildrop;
import org.xsocket.mail.pop3.spi.IPOP3MaildropService;
import org.xsocket.mail.pop3.spi.IPOP3MessageSource;


final class DummyMailDropService implements IPOP3MaildropService {
	private IPOP3Maildrop maildrop = new Maildrop();
	
	public IPOP3Maildrop getMaildrop(String accountId) {
		return maildrop;
	}
	
	private static final class Maildrop implements IPOP3Maildrop {
		
		private Map<String, ByteBuffer> rawMails = new HashMap<String, ByteBuffer>();
		
		Maildrop() {
			lock();
			
			rawMails.put("weewrwr", new DummyMail().getAsByteBuffer());
			rawMails.put("sdfsffs", new DummyMail().getAsByteBuffer());
			rawMails.put("qerwrew", new DummyMail().getAsByteBuffer());
		}
		
		private void lock() {
			
		}
		
		private void unlock() {
			
		}
		
		public int getMessageCount() {
			return rawMails.size();
		}
		
		
		public String[] getAllMessagesUid() {
			Set<String> s = rawMails.keySet();
			return s.toArray(new String[s.size()]);
		}
		
		public Integer getMessageSize(String uid) {
			if (rawMails.containsKey(uid)) {
				ByteBuffer buffer = rawMails.get(uid);
				return buffer.limit() - buffer.position();
				
			} else {
				return null;
			}
		}
	
		
		
		public IPOP3MessageSource getMessage(String uid) {
			if (rawMails.containsKey(uid)) {
				final ByteBuffer buffer = rawMails.get(uid);
				return new IPOP3MessageSource() {
					ByteBuffer copy = buffer.duplicate();
					public ByteBuffer read() throws IOException {
						ByteBuffer result = copy;
						copy = null;
						return result;
					}
				};
				
			} else {
				return null;
			}
		}
		
		
		public IPOP3MessageSource getMessageHeaderAndFirstBodyLines(String uid, int bodyLines) {
			// todo fix me
			return getMessage(uid);
		}
		
		public boolean deletedMessage(String uid) {
			ByteBuffer buffer = rawMails.remove(uid);
			return (buffer != null);
		}
		
		public void close() {
			unlock();
		}
	}
	

	private static final class CutSource implements IPOP3MessageSource {
		private IPOP3MessageSource delegee = null;
		private boolean headerFound = false;
		private Extractor extractor = null;
		private int count = 0;
		private boolean finished = false;

		CutSource(IPOP3MessageSource delegee, int count) {
			this.delegee = delegee;
			this.count = count;
			extractor = new Extractor("\r\n\r\n".getBytes(), 1);
		}
		
		public ByteBuffer read() throws IOException {
			
			if (finished) {
				return null;
			}
			
			
			ByteBuffer buffer = delegee.read();
			if (buffer == null) {
				return null;
			} 
			
			if (!headerFound) {
				Integer pos = extractor.find(buffer);
				buffer.flip();
				if (pos == null) {
					return buffer;
				} else {
					buffer.position(pos);
					buffer = buffer.slice();
					headerFound = true;
					extractor = new Extractor("\r\n".getBytes(), count);
				}
			}
			
			Integer pos = extractor.find(buffer);
			buffer.flip();
			if (pos == null) {
				return buffer;
			} else {
				buffer.position(pos);
				buffer = buffer.slice();
				finished = true;
				return buffer;
			}
		}
	}
	
	
	
	private static final class Extractor {
		private byte[] separator = null;
		private int count = 0;
		
		private int pos = 0;
		
		Extractor(byte[] separator, int count) {
			this.separator = separator;
			this.count = count;
		}
		
		public Integer find(ByteBuffer buffer) {
			while (buffer.hasRemaining()) {
				if (buffer.get() == separator[pos]) {
					pos++;
					if (pos > separator.length) {
						return pos;
					}
				} else {
					pos = 0;
				}
			}
			
			return null;
		}
	}
}