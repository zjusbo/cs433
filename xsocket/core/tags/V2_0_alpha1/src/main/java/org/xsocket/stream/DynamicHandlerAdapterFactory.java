// $Id: MultithreadedServer.java 1629 2007-08-01 06:14:37Z grro $
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
package org.xsocket.stream;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;


import org.xsocket.ILifeCycle;




/**
 * Adapter factory to supported dynamic handlers which declares the call back methods without implementing the appropriated interfaces  
 *   
 * @author grro@xsocket.org
 */
final class DynamicHandlerAdapterFactory  {
	
	private static final Logger LOG = Logger.getLogger(DynamicHandlerAdapterFactory.class.getName());

	private static final DynamicHandlerAdapterFactory INSTANCE = new DynamicHandlerAdapterFactory();
	
	
	private static final int MAX_METHOD_MAP_CACHE_SIZE = 10;
	private static final LinkedHashMap<Class, Map<String, Method>> cachedMethodMaps = new LinkedHashMap<Class, Map<String, Method>>(MAX_METHOD_MAP_CACHE_SIZE) {
																							private static final long serialVersionUID = -4481693873559847580L;

																							protected boolean removeEldestEntry(Map.Entry<Class,java.util.Map<String,Method>> eldest) {
																								return size() > MAX_METHOD_MAP_CACHE_SIZE;
																							};	
																					  };
	
	
	 
	
	/**
	 * returns the singleton instance
	 * @return
	 */
	public static DynamicHandlerAdapterFactory getInstance() {
		return INSTANCE;
	}
	

	static Map<String, Method> getHandlerMethods(Class appHandlerClass) {
		
		Map<String, Method> methodMap = cachedMethodMaps.get(appHandlerClass);
		if (methodMap != null) {
			return methodMap;
		} 
		
		methodMap = new HashMap<String, Method>();
		
		Method[] meths = appHandlerClass.getMethods();
		for (Method meth : meths) {
			String methodName = meth.getName();
				
			if (methodName.equals("onConnect") && (meth.getParameterTypes().length == 1)) {
				methodMap.put(methodName, meth);
						
			} else if (methodName.equals("onDisconnect") && (meth.getParameterTypes().length == 1)) {
				methodMap.put(methodName, meth);
					
			} else if (methodName.equals("onData") && (meth.getParameterTypes().length == 1)) {
				methodMap.put(methodName, meth);
					
			} else if (methodName.equals("onIdleTimeout") && (meth.getParameterTypes().length == 1)) {
				methodMap.put(methodName, meth);
	
			} else if (methodName.equals("onConnectionTimeout") && (meth.getParameterTypes().length == 1)) {
				methodMap.put(methodName, meth);

			} else if (methodName.equals("onInit") && (meth.getParameterTypes().length == 0)) {
				methodMap.put(methodName, meth);
					
			} else if (methodName.equals("onDestroy") && (meth.getParameterTypes().length == 0)) {
				methodMap.put(methodName, meth);
			
			} else if (methodName.equals("clone") && (meth.getParameterTypes().length == 0)) {
				methodMap.put(methodName, meth);
				
			} else if (methodName.startsWith("set") && (meth.getParameterTypes().length == 1)) {
				methodMap.put(methodName, meth);

			} else if (methodName.startsWith("get") && (meth.getParameterTypes().length == 0)) {
				methodMap.put(methodName, meth);
			}
		}
		
		cachedMethodMaps.put(appHandlerClass, methodMap);
		
		return methodMap;
	}

	
	
	/**
	 * creates an adapter for the given dynamic handler
	 * 
	 * @param dynamicHandler   the handler
	 * @return the adapter for the given handler  
	 */
	IHandler createHandlerAdapter(IoHandlerContext handlerCtx, Object handler) {
		return new DynamicHandlerAdapter(handlerCtx, handler).createProxy();
	}
	

	static final class DynamicHandlerAdapter implements InvocationHandler, Cloneable {

		private Map<String, Method> methodMap = null;
		private Class[] supportedInterfaces = null;
		private Object delegee = null;

		
		DynamicHandlerAdapter(IoHandlerContext handlerCtx, Object delegee) {
			this.delegee = delegee;
			methodMap = getHandlerMethods(delegee.getClass());			
			
			Set<Class> classes = new HashSet<Class>();
			if (handlerCtx.isAppHandlerListenForConnectEvent()) {
				classes.add(IConnectHandler.class);
			} 
			
			if (handlerCtx.isAppHandlerListenforDisconnectEvent()) {
				classes.add(IDisconnectHandler.class);
			}

			if (handlerCtx.isAppHandlerListenForDataEvent()) {
				classes.add(IDataHandler.class);
			}
			
			if (handlerCtx.isAppHandlerListenForTimeoutEvent()) {
				classes.add(ITimeoutHandler.class);
			}

			if (handlerCtx.isLifeCycleHandler()) {
				classes.add(ILifeCycle.class);
			}
			
			if (handlerCtx.isConnectionScoped()) {
				classes.add(IConnectionScoped.class);
			}

			supportedInterfaces = classes.toArray(new Class[classes.size()]);
			
			if (LOG.isLoggable(Level.FINE)) {
				StringBuilder sb = new StringBuilder();
				for (Class clazz : supportedInterfaces) {
					sb.append(clazz.getSimpleName() + " ");
				}
				
				LOG.fine("detected interfaces for handler " + delegee.getClass().getName() + ": " + sb.toString().trim());
			}
		}
		
		
		IHandler createProxy() {
			if (supportedInterfaces.length == 0) {
				return new IHandler() { };
				
			} else {
				IHandler hdl = (IHandler) Proxy.newProxyInstance(IHandler.class.getClassLoader(), supportedInterfaces, this);
				return hdl;
			}
		}
		
		
		void injectServerContextField(IServerContext ctx) {
			Field[] fields = delegee.getClass().getDeclaredFields();
			for (Field field : fields) {
				if ((field.getType() == IServerContext.class) && field.isAnnotationPresent(Resource.class)) {
					field.setAccessible(true);
					try {
						field.set(delegee, ctx);
					} catch (IllegalAccessException iae) {
						LOG.warning("could not set HandlerContext for attribute " + field.getName() + ". Reason " + iae.toString());
					}				
				}
			}			
		}
		
	
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String methodName = method.getName();
			
			// clone method? 
			if (methodName.equals("clone") && (args == null)) {
				return clone();
			}
			
			
			// ... no a  business method
			Method meth = methodMap.get(methodName);
			
			if (meth != null) {
				try {
					Object result = meth.invoke(delegee, args);
					
					if (methodName.equals("onConnect") || methodName.equals("onDisconnect") || methodName.equals("onData") || methodName.equals("onIdleTimeout") || methodName.equals("onConnectionTimeout")) {
						if (result != null) {
							if (result instanceof Boolean) {
								return (Boolean) result;
							}
						}
						
						return true;
					} else if (methodName.equals("clone")) {
						DynamicHandlerAdapter copy = (DynamicHandlerAdapter) this.clone();
						copy.delegee = result;
						return copy.createProxy();
					} 							
					
					return null;
				} catch (InvocationTargetException ite) {
					throw ite.getTargetException();
				}
			} else {
				return null;
			}
		}
		
		
		@Override
		protected Object clone() throws CloneNotSupportedException {
			DynamicHandlerAdapter copy = (DynamicHandlerAdapter) super.clone();
			try {
				copy.delegee = methodMap.get("clone").invoke(delegee, (Object[]) null);
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage());
			}
			
			return Proxy.newProxyInstance(IHandler.class.getClassLoader(), supportedInterfaces, copy);
		}
	}
}
