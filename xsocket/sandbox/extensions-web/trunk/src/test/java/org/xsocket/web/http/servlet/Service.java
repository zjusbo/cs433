package org.xsocket.web.http.servlet;

public class Service implements IService {

	public int calculate(int i1, int i2, AddressDTO address) {
		return i1 * i2;
	}
}
