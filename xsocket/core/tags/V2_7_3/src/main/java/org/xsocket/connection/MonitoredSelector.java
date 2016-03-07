/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;




/**
 * monitored selector 
 *
 * @author grro@xsocket.org
 */
abstract class MonitoredSelector {
    
    private static final Logger LOG = Logger.getLogger(MonitoredSelector.class.getName());
    
    private static final boolean IS_LOOPING_CHECK_ACTIVATED = Boolean.parseBoolean(System.getProperty("org.xsocket.connection.selector.looping.check", "true"));
    private static final boolean IS_REINIT_ACTIVATED = Boolean.parseBoolean(System.getProperty("org.xsocket.connection.selector.looping.reinit", "true"));    

    
    private static final long LOG_PERIOD_MILLIS = 5 * 1000;
    private static final int LOOPING_DETECTED_WAIT_TIME_MILLIS = 20;

    private static final int ZERO_COUNT_THRESHOLD = 100;
    private static final int ZERO_COUNT_TIME_THRESHOLD_MILLIS = 10;
    
    
    private int countEventCountIsZero = 0;
    private long lastTimeEventCountIsZero = 0;

    private long lastTimeSpinningLog = 0;

    
    
    final protected void checkForLooping(int eventCount) {
        
        if (IS_LOOPING_CHECK_ACTIVATED) {
        
            if (eventCount == 0) {
                countEventCountIsZero++;
                
                if (countEventCountIsZero == 1) {
                    lastTimeEventCountIsZero = System.currentTimeMillis();
                    countEventCountIsZero++;
                    return;
                    
                } else {
                    if (countEventCountIsZero > ZERO_COUNT_THRESHOLD) {
                        long current = System.currentTimeMillis();
                        if (current < (lastTimeEventCountIsZero + ZERO_COUNT_TIME_THRESHOLD_MILLIS)) {
                            if (current > (lastTimeSpinningLog + LOG_PERIOD_MILLIS)) {
                                lastTimeSpinningLog = current;
                                LOG.warning("selector is looping\r\n" + printRegistered());
                            }
                            
                            if (IS_REINIT_ACTIVATED) {
                                try {
                                    reinit();
                                } catch (IOException ioe) {
                                    if (LOG.isLoggable(Level.FINE)) {
                                        LOG.fine("could not re-init selector " + ioe.toString());
                                    }
                                }
                            }

                            
                            try {
                                Thread.sleep(LOOPING_DETECTED_WAIT_TIME_MILLIS);
                            } catch (InterruptedException ie) { 
                            	// Restore the interrupted status
                                Thread.currentThread().interrupt();
                            }
                        }
                        
                        countEventCountIsZero = 0;
                    }
                }
                
            } else {
                countEventCountIsZero = 0;
            }
        }
    }
    

    abstract String printRegistered();
    
    
    abstract void reinit() throws IOException;
}
