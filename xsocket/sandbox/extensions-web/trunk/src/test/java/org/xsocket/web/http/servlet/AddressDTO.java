package org.xsocket.web.http.servlet;
import java.io.Serializable;


public final class AddressDTO implements Serializable {
	
	public String name = null;
	public int zip = 0;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getZip() {
		return zip;
	}
	public void setZip(int zip) {
		this.zip = zip;
	}
}
