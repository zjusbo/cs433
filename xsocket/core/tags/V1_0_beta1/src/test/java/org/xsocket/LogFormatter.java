// $Id$
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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
package org.xsocket;

import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {

	private Date dat = new Date();
	private MessageFormat formatter = new MessageFormat("{0,date} {0,time}");

    private Object args[] = new Object[1];

	
	@Override
	public String format(LogRecord record) {
		StringBuffer sb = new StringBuffer();
		// Minimize memory allocations here.
		dat.setTime(record.getMillis());
		args[0] = dat;
		StringBuffer text = new StringBuffer();

		formatter.format(args, text, null);
		sb.append(text);
		
		sb.append(" ");
		sb.append(record.getThreadID());

		sb.append(" ");
		sb.append(record.getLevel());
		
		sb.append(" [");
		String clazzname = record.getSourceClassName();
		int i = clazzname.lastIndexOf(".");
		clazzname = clazzname.substring(i + 1, clazzname.length());
		sb.append(clazzname);

		sb.append("#");
		sb.append(record.getSourceMethodName());

		sb.append("] ");
		sb.append(record.getMessage() + "\n");

		return sb.toString();
	}

}
