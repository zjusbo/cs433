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
package org.xsocket.mail.smtp.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeMessage;

import org.xsocket.mail.smtp.spi.IReceiptJournal;
import org.xsocket.mail.smtp.spi.ISmtpMessageSink;
import org.xsocket.mail.smtp.spi.ISmtpMessageSinkService;
import org.xsocket.mail.smtp.spi.SmtpRejectException;


/**
 * 
 * 
 * @author grro@xsocket.org
 */
final class ExampleDataSinkService implements ISmtpMessageSinkService {
	
	private Pattern reversePathPattern = null;
	private Pattern forwardPathPattern = null;
	
	
	public void setReversePathPattern(String pattern) {
		reversePathPattern = Pattern.compile(pattern);
	}

	public void setForwardPathPattern(String pattern) {
		forwardPathPattern = Pattern.compile(pattern);
	}

	
	public void isForwardPathAccepted(IReceiptJournal receiptJournal) throws SmtpRejectException {
		for (String forwardPath : receiptJournal.getSmtpForwardPaths()) {
			boolean isValid = forwardPathPattern.matcher(forwardPath).find();
			if (!isValid) {
				throw new SmtpRejectException(550, "User " + forwardPath + " not local");
			}
		}
	}
	
	
	public void isReversePathAccepted(IReceiptJournal receiptJournal) throws SmtpRejectException {
		String reversePath = receiptJournal.getSmtpReversePath();
		boolean isValid = reversePathPattern.matcher(reversePath).find();
		if (!isValid) {
			throw new SmtpRejectException(550, "not accepted");
		}
	}
	
	
	public ISmtpMessageSink newMessageSink(IReceiptJournal receiptJournal) throws IOException, SmtpRejectException {
		return new AndYouMessageSink(receiptJournal.getSmtpReversePath(), receiptJournal.getSmtpForwardPaths().toArray(new String[receiptJournal.getSmtpForwardPaths().size()]));
	}
	
	
	private static final class AndYouMessageSink implements ISmtpMessageSink {
		
		private boolean isOpen = true; 
		
		private String reversePath = null;
		private String[] forwardPaths = null;
		
		private InputStreamAdapter inputStreamAdapter = new InputStreamAdapter();
		
		AndYouMessageSink(String reversePath, String... forwardPaths) {
			this.reversePath = reversePath;
			this.forwardPaths = forwardPaths;
		}
		
		public boolean isOpen() {
			return isOpen;
		}
		
		public int write(ByteBuffer buffer) throws IOException, SmtpRejectException {
			inputStreamAdapter.addBuffer(buffer);
			return buffer.remaining();
		}
		
		public void close() throws IOException {
			try {
				MimeMessage msg = new MimeMessage(null, inputStreamAdapter);
				Multipart mp = (Multipart) msg.getContent();
				
				BodyPart textPart = mp.getBodyPart(0);
				String text = (String) textPart.getContent();
				
				BodyPart signaturePart = mp.getBodyPart(1);
				int i = Integer.parseInt((String) signaturePart.getContent());
				
				if (text.length() != i) {
					throw new SmtpRejectException(550, "not accepted");
				}
			} catch (MessagingException e) {
				throw new IOException(e);
			}
				
			isOpen = false;
		}
		
		public void delete() {
		}
	}
	
	
	private static final class InputStreamAdapter extends InputStream  {
		
		private List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
		
		
		void addBuffer(ByteBuffer buffer) {
			buffers.add(buffer);
		}
		
		@Override
		public int read() throws IOException {
			if (buffers.isEmpty()) {
				return -1;
			}
			
			ByteBuffer buffer = buffers.get(0);
			if (buffer.hasRemaining()) {
				return (int) buffer.get();
			} else {
				buffers.remove(buffer);
				return read();
			}
		}
	}
}