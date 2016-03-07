// $Id: DataConverter.java 1546 2007-07-23 06:07:56Z grro $

/*
 *  Copyright (c) xcache.org, 2007. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xcache.org/
 */
package org.xcache;



import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.jcache.JCache;
import net.sf.jsr107cache.Cache;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.JmxServer;
import org.xsocket.QAUtil;
import org.xsocket.stream.StreamUtils;





/**
*
* @author grro@xcache.org
*/
public final class StoreServerTest {
	
	private static final AtomicInteger num = new AtomicInteger();
	
	@Test 
	public void testStaticSingleServer() throws Exception {
		
		QAUtil.setLogLevel("org.xcache", Level.FINE);
		
		JmxServer jmxServer = new JmxServer();
		jmxServer.start("test5");
	
		
		CacheServer storeServer = new CacheServer(getCacheFactory());
		StreamUtils.start(storeServer);
		CacheServerMBeanProxyFactory.createAndRegister(storeServer);
		
		StaticCacheClientManager cacheMgr = new StaticCacheClientManager();
		cacheMgr.addCacheServer(storeServer.getLocalAddress(), storeServer.getLocalPort());
		
		Cache cache = cacheMgr.getCache();
		
		
		Assert.assertTrue(storeServer.getCacheSize() == 0);
		Object old= cache.put("1", "test1");
		Assert.assertNull(old);
		QAUtil.sleep(200);
	
		Assert.assertTrue(storeServer.getCacheSize() == 1);
		old = cache.put("2", "test2test3test4test5test6test7");
		Assert.assertNull(old);
		QAUtil.sleep(200);
		Assert.assertTrue(storeServer.getCacheSize() == 2);
		
		Object value = cache.get("2");
		Assert.assertEquals("test2test3test4test5test6test7", value);
		
		old = cache.put("1", "test");
		Assert.assertEquals("test1", old);
		QAUtil.sleep(200);
		Assert.assertTrue(storeServer.getCacheSize() == 2);
	
		old = cache.put("3", new Integer(6));
		QAUtil.sleep(200);
		Assert.assertTrue(storeServer.getCacheSize() == 3);
	
		value = cache.get("3");
		Assert.assertEquals(new Integer(6), value);
		
		value = cache.get("3");
		Assert.assertEquals(new Integer(6), value);
	
		
		value = cache.get("1");
		Assert.assertEquals("test", value);
		
	
		value = cache.get("9");
		Assert.assertNull(value);
	
		jmxServer.stop();
		storeServer.close();
	}


	@Test 
	public void testStatic() throws Exception {
		
		QAUtil.setLogLevel("org.xcache", Level.FINE);
		
		JmxServer jmxServer = new JmxServer();
		jmxServer.start("test");

		
		CacheServer storeServerA = new CacheServer(getCacheFactory());
		StreamUtils.start(storeServerA);
		CacheServerMBeanProxyFactory.createAndRegister(storeServerA);
		
		CacheServer storeServerB = new CacheServer(getCacheFactory());
		StreamUtils.start(storeServerB);
		CacheServerMBeanProxyFactory.createAndRegister(storeServerB);
		
		StaticCacheClientManager cacheMgr = new StaticCacheClientManager();
		cacheMgr.addCacheServer(storeServerA.getLocalAddress(), storeServerA.getLocalPort());
		cacheMgr.addCacheServer(storeServerB.getLocalAddress(), storeServerB.getLocalPort());
		
		Cache cache = cacheMgr.getCache();
		
		
		Object old= cache.put("1", "test1");
		Assert.assertNull(old);
		QAUtil.sleep(200);

		old = cache.put("2", "test2test3test4test5test6test7");
		Assert.assertNull(old);
		QAUtil.sleep(200);
		
		Object value = cache.get("2");
		Assert.assertEquals("test2test3test4test5test6test7", value);
		
		old = cache.put("1", "test");
		Assert.assertEquals("test1", old);
		QAUtil.sleep(200);

		old = cache.put("3", new Integer(6));
		QAUtil.sleep(200);

		value = cache.get("3");
		Assert.assertEquals(new Integer(6), value);
		
		value = cache.get("3");
		Assert.assertEquals(new Integer(6), value);

		
		value = cache.get("1");
		Assert.assertEquals("test", value);
		

		value = cache.get("9");
		Assert.assertNull(value);

		jmxServer.stop();
		storeServerA.close();
		storeServerB.close();
	}
	
	
	@Test 
	public void testDynamicGroup() throws Exception {
		
		QAUtil.setLogLevel("org.xcache", Level.FINE);
		
		JmxServer jmxServer = new JmxServer();
		jmxServer.start("test");

		
		int segments = 5;
		DynamicCacheClientManager locator = new DynamicCacheClientManager(segments);

		CacheServer storeServerA = new CacheServer(getCacheFactory());
		StreamUtils.start(storeServerA);
		CacheServerMBeanProxyFactory.createAndRegister(storeServerA);
		
		locator.addCacheServer(storeServerA.getLocalAddress(), storeServerA.getLocalPort());
		
		
		Cache cache = locator.getCache(); 

		for (int i = 0; i < segments * 21; i++) {
			cache.put(i, i + "sdgfsfsfsfsfsfasdfasfasdfasdfsadfsafasdfasdfasdvsasdfsafsafsdfasfsadfsadfsaffd");
			String data = (String) cache.get(i);
			Assert.assertTrue(data.startsWith(Integer.toString(i)));
		}


		CacheServer storeServerB = new CacheServer(getCacheFactory());
		StreamUtils.start(storeServerB);
		CacheServerMBeanProxyFactory.createAndRegister(storeServerB);
		
		locator.addCacheServer(storeServerB.getLocalAddress(), storeServerB.getLocalPort());
		for (int i = 0; i < segments * 21; i++) {
			String data = (String) cache.get(i);
			Assert.assertTrue(data.startsWith(Integer.toString(i)));
		}

		jmxServer.stop();
		storeServerA.close();
	}

	
	
	
	
	private ICacheFactory getCacheFactory() {
		
		return new ICacheFactory() {
			public Cache newCache() {
				net.sf.ehcache.Cache ehcache = new net.sf.ehcache.Cache(Integer.toString(num.incrementAndGet()), 100, true, true, 0, 0);
				CacheManager.getInstance().addCache(ehcache);
				JCache jcache = new JCache(ehcache, null);
				return jcache;
			}
		};
	}
}
