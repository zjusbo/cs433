// $Id: IntrospectionBasedDynamicBean.java 1175 2007-04-18 18:30:43Z grro $
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
package org.xsocket.stream.management;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;


/**
 * introspection based dynamic mbean, which exposes the getter and setter methods 
 * (all visibilities) of the underlying object by using introspection
 * 
 * @author grro@xsocket.org
 */
final class IntrospectionBasedDynamicBean implements DynamicMBean {
	
	private Object obj = null;
	
	private final Map<String, Info> properties = new HashMap<String, Info>();

	/**
	 * constructore
	 *  
	 * @param obj  the object to create a mbean for
	 */
	IntrospectionBasedDynamicBean(Object obj) {
		this.obj = obj;
	}
	
	
	/**
	 * @see javax.management.DynamicMBean#getAttribute(java.lang.String)
	 */
	public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
		String methodName = "get" + attribute;
		try {
			Method method = getMethod(obj.getClass(), methodName, new Class[0]);
			method.setAccessible(true);
			return method.invoke(obj, new Object[0]);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ReflectionException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	private Method getMethod(Class clazz, String methodname, Class[] params) {
		do {
			try {
				Method method = clazz.getDeclaredMethod(methodname, params);
				method.setAccessible(true);
				return method;
			} catch (Exception ignore) { }
			
			for (Class interf : clazz.getInterfaces()) {
				getMethod(interf, methodname, params);
			}
			
			clazz = clazz.getSuperclass();
			
		} while (clazz != null);
		
		return null;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public AttributeList getAttributes(String[] attributes)  {
		AttributeList list = new AttributeList();
		for (String attribute : attributes) {
			try {
				list.add(new Attribute(attribute, getAttribute(attribute)));
			} catch (Exception ignore) { } 
		}
		return list;
	}


	/**
	 * {@inheritDoc}
	 */
	public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
		String methodName = "set" + attribute.getName();
		
		Info info = getInfo(attribute.getName()); 
		
		try {
			Method method = getMethod(obj.getClass(), methodName, new Class[] { info.propertyType }); 
			method.setAccessible(true);
			method.invoke(obj, new Object[] { attribute.getValue() });
		} catch (Exception e) {
			throw new ReflectionException(e);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public AttributeList setAttributes(AttributeList attributes) {
		AttributeList result = new AttributeList();

		Attribute[] attrs = (Attribute[]) attributes.toArray(new Attribute[attributes.size()]);
        for (Attribute attr : attrs) {
        	try {
                setAttribute(attr);
                result.add(new Attribute(attr.getName(), attr.getValue()));
            } catch (Exception ignore) { }	
        }
        return result;
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
		// TODO Auto-generated method stub
		return null;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public synchronized MBeanInfo getMBeanInfo() {
		
		analyze(obj);

		String[] attributes = properties.keySet().toArray(new String[properties.size()]);
		MBeanAttributeInfo[] attrs = new MBeanAttributeInfo[attributes.length];
		for (int i = 0; i < attrs.length; i++) {
			attrs[i] = properties.get(attributes[i]).asbMBeanAttributeInfo();
		}

	        
		return new MBeanInfo(
				obj.getClass().getName(),
				"",
				attrs,
				null,  // constructors
				null,
				null); // notifications
	    }
	
	
	private void analyze(Object obj) {
		Class clazz = obj.getClass();
		do {
			analyzeType(clazz);
			
			for (Class interf: clazz.getInterfaces()) {
				analyzeType(interf);
			}
			
			clazz = clazz.getSuperclass();
			
		} while (clazz != null);
	}
	
	private void analyzeType(Class clazz) {
		for (Method method : clazz.getDeclaredMethods()) {
			String name = method.getName();
			
			if ((name.length() > 3) && name.startsWith("get")) {
				if (method.getParameterTypes().length == 0) {
					Class propertyType = method.getReturnType();
				
					if(isAcceptedPropertyType(propertyType)) {
						Info info = getInfo(name.substring(3, name.length()));
						info.isReadable = true;
						info.propertyType = propertyType;
					}
				}
			} 
			
			if ((name.length() > 3) && name.startsWith("set")) {
				if (method.getParameterTypes().length == 1) {
					Class propertyType = method.getParameterTypes()[0];
					
					if(isAcceptedPropertyType(propertyType)) {
						Info info = getInfo(name.substring(3, name.length()));
						info.isWriteable = true;
						info.propertyType = propertyType;
					}
				}
			}
		}
	}
	
	
	private Info getInfo(String name) {
		Info info = properties.get(name);
		if (info == null) {
			info = new Info();
			info.propertyName = name;
			info.propertyDescription = "Property " + info.propertyName;
			properties.put(name, info);
		}
		return info;
	}
	
	
	
	
	@SuppressWarnings("unchecked")
	private boolean isAcceptedPropertyType(Class clazz) {
		if (clazz.isAssignableFrom(List.class)) {
			return true;
		}
		
		String name = clazz.getName();
		return name.equals("int")
		       || name.equals("java.lang.Integer")
			   || name.equals("long")
			   || name.equals("java.lang.Long")
			   || name.equals("double")
			   || name.equals("java.lang.Double")
			   || name.equals("boolean")
			   || name.equals("java.lang.Boolean")
			   || name.equals("float")
               || name.equals("java.lang.String")
			   || name.equals("java.lang.Float");
	}

	
	private static class Info {
		String propertyName = null;
		Class  propertyType = null;
		String propertyDescription = null;
		boolean isReadable = false;
		boolean isWriteable = false;
		boolean isIs = false;


		MBeanAttributeInfo asbMBeanAttributeInfo() {
			return new MBeanAttributeInfo(propertyName, propertyType.getName(), propertyDescription, isReadable, isWriteable, isIs);
		}
	}
}
