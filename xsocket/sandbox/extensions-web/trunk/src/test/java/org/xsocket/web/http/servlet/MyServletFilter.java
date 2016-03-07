package org.xsocket.web.http.servlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.springframework.web.context.support.WebApplicationContextUtils;

public class MyServletFilter implements Filter {
	
	private SomeBean someBean = null;
	
	public void init(FilterConfig filterConfig) throws ServletException {
		someBean = (SomeBean) WebApplicationContextUtils.getWebApplicationContext(filterConfig.getServletContext()).getBean("someBean");
	}
	
	public void destroy() { }

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		chain.doFilter(new RequestWrapper(request), response);
	}
	
	
	private final class RequestWrapper extends HttpServletRequestWrapper {

		private Long accountId = null;
		
		public RequestWrapper(ServletRequest request) {
			super((HttpServletRequest) request);
		}
		
		
		@Override
		public String getParameter(String name) {

			if (name.equals("test")) {
				if (accountId == null) {
					someBean.someMethod();
					accountId = new Long(65756);
				}
				return accountId.toString();
			} else {
				return super.getParameter(name);
			}
		}
	}
}