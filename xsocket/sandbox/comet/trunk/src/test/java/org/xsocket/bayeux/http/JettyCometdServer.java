package org.xsocket.bayeux.http;



import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import javax.management.JMException;
import javax.management.ObjectName;


import org.mortbay.cometd.AbstractBayeux;
import org.mortbay.cometd.BayeuxService;
import org.mortbay.cometd.ClientImpl;
import org.mortbay.cometd.SuspendingCometdServlet;
import org.mortbay.cometd.continuation.ContinuationClient;
import org.mortbay.cometd.continuation.ContinuationCometdServlet;
import org.mortbay.cometd.ext.TimesyncExtension;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.MovedContextHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import dojox.cometd.Bayeux;
import dojox.cometd.Client;
import dojox.cometd.RemoveListener;




public class JettyCometdServer {

	private AbstractBayeux bayeux = null;
	private Server server = null;
	
	private String path = null;
	
	

	
	public JettyCometdServer(String path) {
		this.path = path;
	}
	
	
	public void start(int port) throws Exception {
    
	    
	    server = new Server();
	    SelectChannelConnector connector=new SelectChannelConnector();
	    connector.setPort(port);
	    server.addConnector(connector);
	    
	    
	    ContextHandlerCollection contexts = new ContextHandlerCollection();
	    server.setHandler(contexts);
	    
	    
	    Context context = new Context(contexts, "", Context.NO_SECURITY | Context.SESSIONS);
	    
   
	    // Cometd servlet
	    SuspendingCometdServlet cometd_servlet = new SuspendingCometdServlet();
	    ServletHolder cometd_holder = new ServletHolder(cometd_servlet);
	    cometd_holder.setInitParameter("timeout","180000");
	    cometd_holder.setInitParameter("interval","0");
	    cometd_holder.setInitParameter("maxInterval","10000");
	    cometd_holder.setInitParameter("multiFrameInterval","1500");
	    cometd_holder.setInitParameter("JSONCommented","true");
	    context.addServlet(cometd_holder, "/cometd/*");
	    
	    server.start();
	    
	    bayeux = cometd_servlet.getBayeux();
	  
	    /*
	    bayeux.addExtension(new TimesyncExtension());
	    
	    bayeux.setSecurityPolicy(new AbstractBayeux.DefaultPolicy(){
	        public boolean canHandshake(Message message)
	        {
	            if (_testHandshakeFailure<0)
	            {
	                _testHandshakeFailure++;
	                return false;
	            }
	            return true;
	        }
	        
	    });*/
	}
	
	
	
	void stop() throws Exception {
		server.stop();
	}
	
	Bayeux getBayeux() {
		return bayeux;
	}
	
	

    public static class ChatService extends BayeuxService {
        ConcurrentMap<String, Set<String>> _members = new ConcurrentHashMap<String,Set<String>>();
        
        public ChatService(Bayeux bayeux) {
            super(bayeux, "service");
            subscribe("/cometd/**","trackMembers");
        }
        
        public void trackMembers(Client joiner, String channel, Map<String,Object> data,String id) {
            if (Boolean.TRUE.equals(data.get("join"))) {
                Set<String> m = _members.get(channel);
                if (m==null) {
                    Set<String> new_list=new CopyOnWriteArraySet<String>();
                    m=_members.putIfAbsent(channel,new_list);
                    if (m==null)
                        m=new_list;
                }
                
                final Set<String> members=m;
                final String username=(String)data.get("user");
                
                members.add(username);
                joiner.addListener(new RemoveListener(){
                    public void removed(String clientId, boolean timeout)
                    {
                        members.remove(username);
                    }
                });
                
                send(joiner,channel,members,id);
            }
        }
    }
}
