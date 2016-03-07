package org.xsocket.web.http.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class WarFileParser {

	private InputStream is = null;
	
	private String displayName = null;
	private Map<String, String> contextParam  = new HashMap<String, String>();
	private Map<String, String> servletMapping  = new HashMap<String, String>(); 
	private List<FilterMapping> filterMapping  = new ArrayList<FilterMapping>();
	private List<String> listnerClasses  = new ArrayList<String>();
	private List<Entity> filters = new ArrayList<Entity>();
	private List<Entity> servlets = new ArrayList<Entity>();
	
	WarFileParser(InputStream is) {
		this.is = is;
	}
	
	
	void parse() throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document document = builder.parse(is);
		Node webAppNode = getSingleChildNode(document, "web-app");
		
		displayName = getSingleChildNodeContent(webAppNode, "display-name");
		
		for (Node node : getChildNodes(webAppNode, "context-param")) {
			String name = getSingleChildNodeContent(node, "param-name");
			String value = getSingleChildNodeContent(node, "param-value");
			contextParam.put(name, value);
		}
		
		for (Node node : getChildNodes(webAppNode, "servlet-mapping")) {
			String name = getSingleChildNodeContent(node, "servlet-name");
			String pattern = getSingleChildNodeContent(node, "url-pattern");
			servletMapping.put(name, pattern);
		}
		
		for (Node node : getChildNodes(webAppNode, "filter-mapping")) {
			String name = getSingleChildNodeContent(node, "filter-name");
			String pattern = getSingleChildNodeContent(node, "url-pattern");
			if (pattern != null) {
				filterMapping.add(new FilterMapping(name, pattern, false));
			} else {
				String servletName = getSingleChildNodeContent(node, "servlet-name");
				filterMapping.add(new FilterMapping(name, servletName, true));
			}
		}
		
		for (Node node : getChildNodes(webAppNode, "listener")) {
			listnerClasses.add(getSingleChildNodeContent(node, "listener-class"));
		}
		
		
		for (Node node : getChildNodes(webAppNode, "servlet")) {
			String name = getSingleChildNodeContent(node, "servlet-name");
			String classname = getSingleChildNodeContent(node, "servlet-class");
			String loadOnStartUpString = getSingleChildNodeContent(node, "load-on-startup");
			Integer loadOnStartUp = null;
			if (loadOnStartUpString != null) {
				loadOnStartUp =  Integer.parseInt(loadOnStartUpString);
			}
			
			Map<String, String> initParams = new HashMap<String, String>();
			for (Node paramNode : getChildNodes(node, "init-param")) {
				String paramName = getSingleChildNodeContent(paramNode, "param-name");
				String paramValue = getSingleChildNodeContent(paramNode, "param-value");
				initParams.put(paramName, paramValue);
			}
			
			servlets.add(new Entity(name, classname, loadOnStartUp, initParams));
		}
		
		
		for (Node node : getChildNodes(webAppNode, "filter")) {
			String name = getSingleChildNodeContent(node, "filter-name");
			String classname = getSingleChildNodeContent(node, "filter-class");
			String loadOnStartUpString = getSingleChildNodeContent(node, "load-on-startup");
			Integer loadOnStartUp = null;
			if (loadOnStartUpString != null) {
				loadOnStartUp =  Integer.parseInt(loadOnStartUpString);
			}


			Map<String, String> initParams = new HashMap<String, String>();
			for (Node paramNode : getChildNodes(node, "init-param")) {
				String paramName = getSingleChildNodeContent(paramNode, "param-name");
				String paramValue = getSingleChildNodeContent(paramNode, "param-value");
				initParams.put(paramName, paramValue);
			}
			
			filters.add(new Entity(name, classname, loadOnStartUp, initParams));
		}
	}
	
	
	private List<Node> getChildNodes(Node node, String name) {
		List<Node> list = new ArrayList<Node>();
		
		NodeList nodeList = node.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node childNode = nodeList.item(i);
			if (childNode.getNodeName().equals(name)) {
				list.add(childNode);
			}
		}
		
		return list;
	}
	
	
	private Node getSingleChildNode(Node node, String name) {
		NodeList nodeList = node.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node childNode = nodeList.item(i);
			if (childNode.getNodeName().equals(name)) {
				return childNode;
			}
		}
		
		return null;
	}
	
	
	private String getSingleChildNodeContent(Node node, String name) {
		Node childNode = getSingleChildNode(node, name);
		if (childNode != null) {
			return childNode.getTextContent().trim();
		} else {
			return null;
		}
	}


	public Map<String, String> getContextParam() {
		return contextParam;
	}


	public String getDisplayName() {
		return displayName;
	}

 
	public List<String> getListnerClasses() {
		return listnerClasses;
	}


	public List<FilterMapping> getFilterMapping() {
		return filterMapping;
	}


	public Map<String, String> getServletMapping() {
		return servletMapping;
	}
	
	
	
	final class FilterMapping {
		private String name = null;
		private String value = null;
		private boolean isServletMapping = false;
		
		
		private FilterMapping(String name, String value, boolean isServletMapping) {
			this.name = name;
			this.value = value;
			this.isServletMapping = isServletMapping;
		}
		
		@Override
		public String toString() {
			return name + "=" + value + " (isServletMapping=" + isServletMapping + ")";
		}

		public boolean isServletMapping() {
			return isServletMapping;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}
	}
	
	
	
	final class Entity {
		String name = null;
		String classname = null;
		Integer loadOnStartup = null;
		Map<String, String> initParam = null;
		
		Entity(String name, String classname, Integer loadOnStartup,	Map<String, String> initParam ) {
			this.name = name;
			this.classname = classname;
			this.loadOnStartup = loadOnStartup;
			this.initParam = initParam;
		}

		public String getClassname() {
			return classname;
		}

		public Map<String, String> getInitParam() {
			return initParam;
		}

		public Integer getLoadOnStartup() {
			return loadOnStartup;
		}

		public String getName() {
			return name;
		}
	}


	public List<Entity> getFilters() {
		return filters;
	}


	public List<Entity> getServlets() {
		return servlets;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("displayName=" + displayName + "\n");
		
		for (String name : contextParam.keySet()) {
			sb.append("contextParam " + name + "=" + contextParam.get(name) + "\n");
		}
		
		for (String name : listnerClasses) {
			sb.append("listernerClass " + name + "\n");
		}
		
	
		
		for (Entity entity : filters) {
			sb.append("filter " + entity.getClassname() + ", "  + entity.getClass() + " loadOnStartUp=" + entity.getLoadOnStartup() + "\n");
			for (String name : entity.getInitParam().keySet()) {
				sb.append("initParam " + name + "=" + entity.getInitParam().get(name) + "\n");
			}
		}

		
		for (String name : servletMapping.keySet()) {
			sb.append("servletMapping " + name + "=" + servletMapping.get(name) + "\n");
		}
		
		for (Entity entity : servlets) {
			sb.append("servlet " + entity.getClassname() + ", "  + entity.getClass() + " loadOnStartUp=" + entity.getLoadOnStartup() + "\n");
			for (String name : entity.getInitParam().keySet()) {
				sb.append("initParam " + name + "=" + entity.getInitParam().get(name) + "\n");
			}
		}


		
		return sb.toString();
	}
}
