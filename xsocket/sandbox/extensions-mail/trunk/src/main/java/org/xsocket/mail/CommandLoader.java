// $Id: CommandLoader.java 368 2006-10-23 05:45:29Z grro $

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
package org.xsocket.mail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;



/**
 * command loader
 * 
 * @author grro@xsocket.org
 */
public final class CommandLoader<T> {
	
	private static final Logger LOG = Logger.getLogger(CommandLoader.class.getName());
	
	private Map<String, T> commands = new HashMap<String, T>();

	private String pckgname = null;
	private Class clazz = null;
	
	/**
	 * constructure
	 * 
	 * @param pckgname  the package to scan
	 * @param clazz the reference class
	 */
	public CommandLoader(String pckgname, Class clazz) {
		this.pckgname = pckgname;
		this.clazz = clazz;
	}
	
	/**
	 * get all found commands
	 * 
	 * @return found commands
	 */ 
	public Map<String, T> getCommands() {
		return commands;
	}
	
	/**
	 * get a command 
	 * 
	 * @param name the command name
	 * @return the command object or null 
	 */
	public T getCommand(String name) {
		return commands.get(name);
	}
	
	/**
	 * load the commands
	 *
	 */
	public final void loadCommands() {
		Map<String, T> newCommands = new HashMap<String, T>();
	
		if (LOG.isLoggable(Level.FINE)) {
			LOG.info("load commands (instance of " + clazz.getSimpleName() + ") for package " + pckgname);
		}
		List<URL> pckgUrls = retrievePackageUrls();

		
		for (URL pckgUrl : pckgUrls) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.info("scanning " + pckgUrl);
			}
			
			retrieveClasses(pckgUrl, newCommands);
		}
		
		commands = newCommands;
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("commands loaded:");
			for (String cmd : commands.keySet()) {
				LOG.fine(cmd);	
			}
		}
	}

	
	
	private List<URL> retrievePackageUrls()  {

    	List<URL> directories = new ArrayList<URL>(); 

		
		ClassLoader cld = Thread.currentThread().getContextClassLoader();
        if (cld == null) {
            LOG.warning("Can't get class loader to load commands");
      
        } else {
	        try {
	        	String name = pckgname;
	         	name = name.replace('.','/');
		       	
	        	Enumeration<URL> resources = cld.getResources(name);
	        	while (resources.hasMoreElements()) {
		 			directories.add(resources.nextElement());
		       	}
	        } catch (IOException ioe) {
	        	LOG.warning("error occured by accesing package file to load commands of " + pckgname);
	        }
        }

       	return directories;
	}
	
	
	

	
	
	private void retrieveClasses(URL url, Map<String, T> cmds) {
		
		try {
	   		String resource = URLDecoder.decode(url.getPath(), "UTF-8");
			File directory = new File(resource); 
			if (directory.exists()) {
				String[] files = directory.list();
				for (String file : files) {
					if (file.endsWith(".class")) {
						addInstanceIfCommand(pckgname + '.' + file.substring(0, file.length() - 6), cmds);
					}
				}
			} else {
				JarURLConnection con = (JarURLConnection) url.openConnection();
				String starts = con.getEntryName();
				Enumeration<JarEntry> entriesEnum = con.getJarFile().entries();

				while (entriesEnum.hasMoreElements()) {
					ZipEntry entry = (ZipEntry) entriesEnum.nextElement();
				    String entryname = entry.getName();

				    if (entryname.startsWith(starts) 
				    	&& (entryname.lastIndexOf('/') <= starts.length()) 
				    	&& entryname.endsWith(".class")) {
				    	
				    	String classname = entryname.substring(0,entryname.length()-6);
				    	if (classname.startsWith("/")) { 
				    		classname = classname.substring(1);
				    	}
				    	classname = classname.replace('/','.');
				    	addInstanceIfCommand(classname, cmds);
				    }
				}
			}
		} catch (IOException ioe) {
			LOG.warning("couldn't retrieve classes of " + url+ ". Reason: " + ioe);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void addInstanceIfCommand(String classname, Map<String, T> cmds) {
		
		try {
			Class clazzToCheck = Class.forName(classname, true, Thread.currentThread().getContextClassLoader());
			if (clazz.isAssignableFrom(clazzToCheck)) {
					clazzToCheck.getConstructors();
					Constructor con = clazzToCheck.getConstructor(new Class[] {});
					con.setAccessible(true);
					T cmd = (T) con.newInstance(new Object[0]);
					String name = cmd.getClass().getAnnotation(CommandName.class).value();
					
					if (cmds.containsKey(name)) {
						LOG.warning("command " + name + " already loaded. Overload existing command");
					}
					cmds.put(name, cmd);
			}
		} catch (Exception e) { 
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("exception occured while load " + classname + ". Reason: " + e.toString());
			}
		}
	}

}
			
