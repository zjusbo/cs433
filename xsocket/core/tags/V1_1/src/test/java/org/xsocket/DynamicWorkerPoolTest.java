// $Id: DynamicWorkerPoolTest.java 1219 2007-05-05 07:36:36Z grro $
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
package org.xsocket;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DynamicWorkerPool;



/**
*
* @author grro@xsocket.org
*/
public final class DynamicWorkerPoolTest {

	
	private boolean sleep = true;

	
	
	@Test public void testMinMax() throws Exception {	
		
		DynamicWorkerPool pool = new DynamicWorkerPool(5, 20);
		pool.setAdjustPeriod(500);
		Assert.assertTrue(pool.getMinimumPoolSize() == 5);
		Assert.assertTrue(pool.getMaximumPoolSize() >= 20);
		Assert.assertTrue(pool.getPoolSize() == 5);
		
		System.out.println(pool);

		// generate load
		for (int i = 0; i < 20; i++) {
			pool.execute(new DummyTask());
		}

		QAUtil.sleep(2000);
		Assert.assertTrue(pool.getActiveCount() > 5);
		Assert.assertTrue(pool.getPoolSize() > 5);
		Assert.assertTrue(pool.getPoolSize() <= 20);
		System.out.println(pool);		
	}

	
	@Test public void testEmpty() throws Exception {
		DynamicWorkerPool pool = new DynamicWorkerPool(0, 20);
		Assert.assertTrue(pool.getMinimumPoolSize() == 0);
		Assert.assertTrue(pool.getMaximumPoolSize() >= 20);
		Assert.assertTrue(pool.getPoolSize() == 1);
		
		System.out.println(pool);

		sleep = false;
		
		// generate load
		for (int i = 0; i < 3; i++) {
			pool.execute(new DummyTask());
		}

		System.out.println(pool);
	}

	
	
	private final class DummyTask implements Runnable {
		
		public void run() {
			do {
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) { }
			} while (sleep); 
		}
	}
	
}
