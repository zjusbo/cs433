package org.xsocket.web.http.servlet;

import java.io.IOException;

abstract class War {
	
	abstract void install(WebAppContainer container, String context) throws IOException;

}
