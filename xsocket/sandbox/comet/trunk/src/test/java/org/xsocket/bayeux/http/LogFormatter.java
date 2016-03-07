package org.xsocket.bayeux.http;




import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;


/**
 * 
 * @author grro
 */
public class LogFormatter extends Formatter {

	private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("yyyy.MM.dd kk:mm:ss,S"); 
	
	
	@Override
	public String format(LogRecord record) {
		StringBuffer sb = new StringBuffer();

		sb.append(DATEFORMAT.format(record.getMillis()));
		
		sb.append(" ");
		sb.append(record.getThreadID());

		sb.append(" ");
		sb.append(record.getLevel());
		
		sb.append(" (");
		String clazzname = record.getSourceClassName();
		int i = clazzname.lastIndexOf(".");
		clazzname = clazzname.substring(i + 1, clazzname.length());
		sb.append(clazzname);

		sb.append("#");
		sb.append(record.getSourceMethodName());

		sb.append(") ");
		sb.append(record.getMessage() + "\n");

		return sb.toString();
	}

}
