package org.xsocket.web.http.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


import javax.servlet.ServletContextListener;

import org.xsocket.web.http.servlet.WarFileParser.FilterMapping;

public final class VirtualWAR extends War {

	private static final Logger LOG = Logger.getLogger(VirtualWAR.class.getName());
	
	
	private final Map<String, Set<String>> fileContent = new HashMap<String, Set<String>>();
	private final Map<String, Set<String>> urlContent = new HashMap<String, Set<String>>();
	
	private boolean isContentCacheActivated = false;

	public void setContentCacheActivated(boolean isContentCacheActivated) {
		this.isContentCacheActivated = isContentCacheActivated;
	}
	
	
	public void add(String contentPath, String sourcePath) {
		
		if (!contentPath.startsWith("/")) {
			contentPath = "/" + contentPath;
		}
		
		if (sourcePath.toUpperCase().startsWith("HTTP://")) {
			Set<String> contentList = urlContent.get(contentPath); 
			
			if (contentList == null) {
				contentList = new HashSet<String>();
				urlContent.put(contentPath, contentList);
			}
			
			if (!sourcePath.endsWith("/")) {
				sourcePath = sourcePath + "/";
			}
			contentList.add(sourcePath);

			
		} else {
			if (new File(sourcePath).exists()) {
				Set<String> contentList = fileContent.get(contentPath); 
				
				if (contentList == null) {
					contentList = new HashSet<String>();
					fileContent.put(contentPath, contentList);
				}
				
				contentList.add(sourcePath);
			} else  {
				LOG.warning("given source path " + sourcePath + " doesn't exist. ignore value");
			}
		}
	}
	
	
	
	@Override
	void install(WebAppContainer container, String contextPath) throws IOException {
		
	
		// looking for web.xml file		
		String webXmlPath = null;
		
		scanLoop: for (String contentPath : fileContent.keySet()) {
			for (String sourcePath : fileContent.get(contentPath)) {
				File file = null;
				if (contentPath.equals("/")) {
					file = new File(sourcePath + File.separator + "WEB-INF" + File.separator + "web.xml");

					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("check if " + file.getAbsolutePath() + " exists");
					}
					if (file.exists()) {
						webXmlPath = file.getAbsolutePath();
						break scanLoop;
					} else {
						LOG.warning("path " + file.getAbsolutePath() + " doesn't exist (" + sourcePath + ")");
					}
				} else if (contentPath.startsWith("/WEB-INF")) {
					file = new File (sourcePath + File.separator + "web.xml");
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("check if " + file.getAbsolutePath() + " exists");
					}
					if (file.exists()) {
						webXmlPath = file.getAbsolutePath();
						break scanLoop;
					}
				}
			}
		}
		
		if (webXmlPath != null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(webXmlPath + " found. analyzing xml file");
			}
			FileInputStream is = new FileInputStream(webXmlPath);
			try {
				WarFileParser parser = new WarFileParser(is);
				try {
					parser.parse();
					
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine(parser.toString());
					}
					
					Context context = new Context(contextPath, parser.getContextParam(), new ContextWorkspace());
					 
					for (String listenerClassname : parser.getListnerClasses()) {
						ServletContextListener listener = (ServletContextListener) newObject(listenerClassname);
						if (listener != null) {
							context.addContextListener(listener);
						}
					}
					
					for (FilterMapping filterMapping : parser.getFilterMapping()) {
						context.addFilterMapping(filterMapping.getName(), filterMapping.getValue(), filterMapping.isServletMapping());
					}
					
					for (WarFileParser.Entity entity : parser.getFilters()) {
						context.addFilter(entity.getName(), Class.forName(entity.getClassname(), true, Thread.currentThread().getContextClassLoader()), entity.getInitParam());
					}

					for (String servletname : parser.getServletMapping().keySet()) {
						context.addServletMapping(servletname, parser.getServletMapping().get(servletname));
					}

					for (WarFileParser.Entity entity : parser.getServlets()) {
						context.addServlet(entity.getName(), Class.forName(entity.getClassname()), entity.getLoadOnStartup(), entity.getInitParam());
					}

					
					container.addContext(context);
				} catch (Exception e) {
					throw new IOException(e);
				}
			} finally {
				is.close();
			}
		} else {
			LOG.warning("no web.xml found");
		}
	}
	
	
	private Object newObject(String classname) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("creating " + classname);
		}
		try {
			Class clazz = Class.forName(classname);
			return clazz.newInstance();
		} catch (Exception e) {
			LOG.warning("Error occured by creating " + classname + ". Reason " + e.toString());
			return null;
		}
	}
	
	
	private final class ContextWorkspace implements IContextWorkspace {
		

		private final Map<String, String> resources = new HashMap<String, String>();
		
		
		
		public String getAbsoluteResourcePath(String ressourcePath) {
			return resolve(ressourcePath, true);
		}
		
		
		public String getAbsolutePath(String ressourcePath) {
			return resolve(ressourcePath, false);
		}
		
		
		private String resolve(String ressourcePath, boolean ignoreWebInf) {
			
			if (resources.containsKey(ressourcePath)) {
				return resources.get(ressourcePath);
			}

			if (!ressourcePath.startsWith("/")) {
				ressourcePath = "/" + ressourcePath; 
			}
			
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("resource " + ressourcePath + " requested");
			}
			
			
			File file = scanFile(ressourcePath, ignoreWebInf);
			if (file == null) {
				file = scanUrl(ressourcePath);
			}
							
			if (file != null) {
				if (isContentCacheActivated) {
					resources.put(ressourcePath, file.getAbsolutePath());
				}
				return file.getAbsolutePath();
			} else {
				return null;
			}
		}
		
		
		
		private File scanFile(String ressourcePath, boolean ignoreWebInf) {
			for (String contentPath : fileContent.keySet()) {
				
				if (ressourcePath.startsWith(contentPath)) {
					String path = ressourcePath.substring(contentPath.length(), ressourcePath.length());

					for (String sourcePath : fileContent.get(contentPath)) {
						
						File file = new File(sourcePath + "/" + path);
						if (LOG.isLoggable(Level.INFO)) {
							LOG.info("check file " + file.getAbsolutePath());
						}
													
						if (file.exists()) {
							if (LOG.isLoggable(Level.INFO)) {
								LOG.info("resource for " + ressourcePath + " found: " + file.getAbsolutePath());
							}
							
							if (ignoreWebInf) {
								if (file.getAbsolutePath().toUpperCase().indexOf("WEB-INF") != -1) {
									if (LOG.isLoggable(Level.INFO)) {
										LOG.info("requested resource is part of WEB-INF path. ignore this resource");
									}
									return null;
								}
							}
							return file;
						}
					}

				}
			}
			
			return null;
		}
		
	
		
		
		private File scanUrl(String ressourcePath) {
			for (String contentPath : urlContent.keySet()) {
				
				if (ressourcePath.startsWith(contentPath)) {
					String path = ressourcePath.substring(contentPath.length(), ressourcePath.length());

					for (String sourcePath : urlContent.get(contentPath)) {
						try {
							URL url = new URL(sourcePath + path);
							InputStream from = url.openStream();
							
							if (LOG.isLoggable(Level.INFO)) {
								LOG.info("loading " + url.toString());
							}
							
							File file = File.createTempFile("page", "tmp");
							file.deleteOnExit();
							FileOutputStream to = new FileOutputStream(file);
							
							byte[] buffer = new byte[4096];
							int bytesRead;

							while ((bytesRead = from.read(buffer)) != -1) {
								to.write(buffer, 0, bytesRead); 
							}
							
							from.close();
							to.close();
							
							return file;
							
						} catch (Exception ignore) { }
					}

				}
			}

			return null;
		}
	}
}
