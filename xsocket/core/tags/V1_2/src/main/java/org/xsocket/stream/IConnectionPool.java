// $Id: BlockingConnection.java 1134 2007-04-05 17:44:43Z grro $
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

import java.io.IOException;

/**
 * a connection pool 
 *
 * @author grro@xsocket.org
 */
public interface IConnectionPool {
	
	
	/**
	 * destroy the given connection. This connection will not return into the pool. It will be
	 * really closed instead. A leased connection should be destroyed by this way, if it is clear
	 * that the connection has become invalid.
	 * 
	 * @param connection   the leased connection
	 * @throws IOException if an exception occurs
	 */
	public void destroyConnection(IConnection connection) throws IOException;
	

	/**
	 * return the number of max active connections
	 * 
	 * @return the number of max active connections
	 */
	public int getMaxActive();
	
	
	/**
	 * set the number of max active connections
	 * 
	 * @param maxActive the number of max active connections
	 */
    public void setMaxActive(int maxActive);
    
    
    /**
     * get the max wait time to get a free connection by 
     * calling the getXXXConnection().   
     * 
     * @return the max wait time to get a free connection
     */
    public long getMaxWaitMillis();

    
    /**
     * set the max wait time to get a free connection by
     * calling the getXXXConnection().
     * 
     * @param maxWaitMillis  the max wait time to get a free connection
     */
    public void setMaxWaitMillis(long maxWaitMillis);

    
    /**
     * get the number of max idle connections 
     * @return the number of max idle connections 
     */
    public int getMaxIdle();

    
    /**
     * set the number of max idle connections 
     * @param maxIdle  the number of max idle connections 
     */
    public void setMaxIdle(int maxIdle);
    
    
    /**
     * get the number of the active (borrowed) connects 
     * @return the number of the active connects
     */
    public int getNumActive();
    
    
    /**
     * get the number of idling connections connections
     *  
     * @return the number of idling connections connections
     */
    public int getNumIdle();
	
    
    /**
     * get the idle timeout for pooled connections
     * @return the idle timeout for pooled connections
     */
	public long getIdleTimeoutMillis();

	
	/**
	 * set the idle timeout for pooled connections
	 * 
	 * @param idleTimeoutMillis the idle timeout for pooled connections
	 */
	public void setIdleTimeoutMillis(long idleTimeoutMillis);

	/** 
	 * get the life timeout
	 * 
	 * @return the life timeout
	 */
	public long getLifeTimeoutMillis();
	
	/**
	 * set the life timeout
	 * @param lifeTimeoutMillis  the life timeout
	 */
	public void setLifeTimeoutMillis(long lifeTimeoutMillis);

		
	/**
	 * closes the connection pool. All free connection of the pool will be closed
	 *
	 */
	public void close();		
}	
