/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection;


import java.io.Closeable;
import java.util.List;

import org.xsocket.ILifeCycle;



/**
 * A connection pool manages a pool of open connections. 
 * Typically, a connection pool will be used on the client-side if connections for the 
 * same server (address) will be created in a serial manner in a sort period of time. 
 * By pooling such connections the overhead of establishing a connection will be avoided
 * For pool management reasons, timeouts can be defined. The
 * IdleTimeout defines the max idle time in the pool. After this time the
 * free connection will be closed. In the same way, the max living time
 * defines the timeout of the connection. If a free connection exceeds
 * this time, the connection will be closed.  <br>
 * Additional the max size of the active connections can be defined.
 * If a connection is requested and the max limit of the active connection
 * is reached, the request will be blocked until a connection becomes free
 * or the maxWaitTime will be reached.
 *
 * @author grro@xsocket.org
 */
public interface IConnectionPool extends Closeable {
	
	public static final int DEFAULT_MAX_ACTIVE = Integer.MAX_VALUE;
	public static final int DEFAULT_MAX_IDLE = Integer.MAX_VALUE;
	public static final long DEFAULT_MAX_WAIT_MILLIS = Long.MAX_VALUE;
	public static final int DEFAULT_IDLE_TIMEOUT_SEC = Integer.MAX_VALUE;
	public static final int DEFAULT_LIFE_TIMEOUT_SEC= Integer.MAX_VALUE;
	public static final int DEFAULT_CREATION_TIMEOUT_MILLIS = 500;

	
	
	/**
	 * returns true, is pool is open
	 * 
	 * @return true, is pool is open
	 */
	public boolean isOpen();
	
	
	/**
	 * adds a listener
	 * 
	 * @param listener the listener to add
	 */
	public void addListener(ILifeCycle listener);

	
	/**
	 * removes a listener
	 * 
	 * @param listener the listener to remove
	 * @return true, is the listener has been removed
	 */
	public boolean removeListener(ILifeCycle listener);

	 
	/**
	 * return the number of max active resources
	 * 
	 * @return number of max active resources
	 */
	public int getMaxActivePooled();

	
	/**
	 * set the number of max active resources
	 * 
	 *  @param maxActive the number of max active resources
	 */
    public void setMaxActivePooled(int maxActive);


    /**
     * get the max waiting by retrieving a resource
     * 
     * @return the max waiting by retrieving a resource
     * 
     */
    public long getCreationMaxWaitMillis();

    
	/**
	 * set the max waiting by retrieving a resource
	 * 
	 * @param maxWaitMillis  the max waiting by retrieving a resource
	 */
    public void setCreationMaxWaitMillis(long maxWaitMillis);


	/**
	 * get the number of max idling resources
	 * 
	 * @return  the number of max idling resources
	 */
    public int getMaxIdlePooled();


	/**
	 * set the number of max idling resources
	 * 
	 * @param the number of max idling resources
	 */
    public void setMaxIdlePooled(int maxIdle);


	/**
	 * get the current number of the active resources 
	 * 
	 * @return the current number of the active resources
	 */
    public int getNumPooledActive();
    
    
	/**
	 * get the number of the destroyed resources 
	 * 
	 * @return the number of the destroyed resources 
	 */
    public int getNumDestroyed();
    
    

    /**
     * get the number of timeouts caused by the pool life timeout
     * @return the number of timeouts
     */
    public int getNumTimeoutPooledLifetime();
    
    
    /**
     * get the number of timeouts caused by the pool idle timeout
     * @return the number of timeouts
     */
    public int getNumTimeoutPooledIdle();

    
    /**
	 * get the number of the created resources 
	 * 
	 * @return the number of the created resources 
	 */
   public int getNumCreated();
   

   /**
    * get a info list about the active connections 
    * @return a info list about the active connection 
    */
   public List<String> getActiveConnectionInfos();
   
   
   /**
    * get a info list about the idle connections 
    * @return a info list about the idle connections 
    */
   public List<String> getIdleConnectionInfos();
   
   
	/**
	 * get the current number of idling resources
	 * 
	 *  @return the current number of idling resources
	 */
    public int getNumPooledIdle();

   
	/**
	 * get the idle time out
	 * 
	 * @return the idle time out
	 */
	public int getPooledIdleTimeoutSec();


	/**
	 * set the idle time out of a resource within the pool
	 * 
	 * @param idleTimeoutSec the idle time out
	 */
	public void setPooledIdleTimeoutSec(int idleTimeoutSec);


	/**
	 * get the life timeout of a resource
	 * 
	 * @return the life timeout of a resource 
	 */
	public int getPooledLifeTimeoutSec();

 
	/**
	 * set the life timeout of a resource 
	 * 
	 * @param lifeTimeoutSec the life timeout of a resource 
	 */
	public void setPooledLifeTimeoutSec(int lifeTimeoutSec);

}
