/// $Id: Dispatcher.java 1751 2007-09-18 07:08:17Z grro $
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
package org.xsocket;


import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * implementation of the {@link IDispatcher}
 * <br><br>
 * All dispatcher methods are thread save.
 *
 * @author grro@xsocket.org
 */
public class Dispatcher<T extends IHandle> implements IDispatcher<T> {

	private static final Logger LOG = Logger.getLogger(Dispatcher.class.getName());

	private static final long TIMEOUT_SHUTDOWN_MILLIS = 5L * 1000L;

	// is open flag
	private volatile boolean isOpen = true;

	// guard object for synchronizing
 	private final Object dispatcherThreadGuard = new Object();

	// connection handling
	private Selector selector = null;

	// event handler
	private IEventHandler<T> eventHandler = null;



	// statistics
    private long statisticsStartTime = System.currentTimeMillis();
	private long handledRegistractions = 0;
	private long handledReads = 0;
	private long handledWrites = 0;

	/**
	 * constructor
	 *
	 * @param eventHandler    the assigned event handler
	 */
	public Dispatcher(IEventHandler<T> eventHandler)  {
		assert (eventHandler != null) : "null is not allowed for event handler ";

		this.eventHandler = eventHandler;


		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("dispatcher " + this.hashCode() + " has been created (eventHandler=" + eventHandler + ")");
		}

		try {
			selector = Selector.open();
		} catch (IOException ioe) {
			String text = "exception occured while opening selector. Reason: " + ioe.toString();
			LOG.severe(text);
			throw new RuntimeException(text, ioe);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public final IEventHandler<T> getEventHandler() {
		return eventHandler;
	}


	/**
	 * {@inheritDoc}
	 */
	public void register(T handle, int ops) throws IOException {
		assert (!handle.getChannel().isBlocking());

		if (LOG.isLoggable(Level.FINE)) {
	        LOG.fine("register handle " + handle);
		}

		synchronized (dispatcherThreadGuard) {
			selector.wakeup();

			handle.getChannel().register(selector, ops, handle);
			eventHandler.onHandleRegisterEvent(handle);
		}


		handledRegistractions++;
	}


	/**
	 * {@inheritDoc}
	 */
	public void deregister(final T handle) throws IOException {

		synchronized (dispatcherThreadGuard) {
			selector.wakeup();

			SelectionKey key = handle.getChannel().keyFor(selector);
			if (key.isValid()) {
				key.cancel();
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public final Set<T> getRegistered() {

		Set<T> registered = new HashSet<T>();

		if (selector != null) {
			SelectionKey[] selKeys = null;
			synchronized (dispatcherThreadGuard) {
				selector.wakeup();

				Set<SelectionKey> keySet = selector.keys();
				selKeys = keySet.toArray(new SelectionKey[keySet.size()]);
			}

			try {
				for (SelectionKey key : selKeys) {
					T handle = (T) key.attachment();
					registered.add(handle);
				}
			} catch (Exception ignore) { }
		}

		return registered;
	}



	/**
	 * {@inheritDoc}
	 */
	public final void updateInterestSet(T handle, int ops) throws IOException {
		SelectionKey key = handle.getChannel().keyFor(selector);

		if (key != null) {
			synchronized (dispatcherThreadGuard) {
				if (key.isValid()) {
					key.selector().wakeup();

					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("updating interest ops for " + handle + ". current value is " + printSelectionKeyValue(key.interestOps()));
					}

					key.interestOps(ops);

					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("interest ops has been updated to " + printSelectionKeyValue(ops));
					}
				} else {
					throw new IOException("handle " + handle + " is invalid ");
				}
			}
		}
	}



	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public final void run() {

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("selector  listening ...");
		}


		while(isOpen) {
			try {

				// see http://developers.sun.com/learning/javaoneonline/2006/coreplatform/TS-1315.pdf
				synchronized (dispatcherThreadGuard) {
					/* suspend the dispatcher thead */
				}

				int eventCount = 0;
				try {

				// waiting for new events (data, ...)
				 eventCount = selector.select(1000);
				} catch (Throwable e) {
					LOG.warning("sync exception occured while processing. Reason " + e.toString());
				}
				// handle read write events
				if (eventCount > 0) {
					Set selectedEventKeys = selector.selectedKeys();
					Iterator it = selectedEventKeys.iterator();

					// handle read & write
					while (it.hasNext()) {
						SelectionKey eventKey = (SelectionKey) it.next();
						it.remove();

						T handle = (T) eventKey.attachment();

						// read data
						if (eventKey.isValid() && eventKey.isReadable()) {

							// notify event handler
							try {
								eventHandler.onHandleReadableEvent(handle);
							} catch (Throwable e) {
								LOG.warning("[" + Thread.currentThread().getName() + "] exception occured while handling readable event. Reason " + e.toString());
							}

							handledReads++;
						}

						// write data
						if (eventKey.isValid() && eventKey.isWritable()) {
							handledWrites++;

							// notify event handler
							try {
								eventHandler.onHandleWriteableEvent(handle);
							} catch (Throwable e) {
								LOG.warning("[" + Thread.currentThread().getName() + "] exception occured while handling writeable event. Reason " + e.toString());
							}
						}
					}
				}

			} catch (Throwable e) {
				LOG.warning("[" + Thread.currentThread().getName() + "] exception occured while processing. Reason " + e.toString());
			}
		}


        closeDispatcher();
	}


	@SuppressWarnings("unchecked")
	private void closeDispatcher() {
        LOG.fine("closing connections");




		if (selector != null) {
			try {
				selector.close();
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by close selector within tearDown " + e.toString());
				}
			}
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public void close() {
		if (isOpen) {
			if (selector != null) {

				// initiate closing of open connections
				Set<T> openHandles = getRegistered();
				final int openConnections = openHandles.size();
				for (T handle : openHandles) {
					eventHandler.onDispatcherCloseEvent(handle);
				}

				// start closer thread
				Thread closer = new Thread() {
					@Override
					public void run() {
						long start = System.currentTimeMillis();

						int terminatedConnections = 0;
						do {
							try {
								Thread.sleep(100);
							} catch (InterruptedException ignore) { }

							if (System.currentTimeMillis() > (start + TIMEOUT_SHUTDOWN_MILLIS)) {
								LOG.warning("shutdown timeout reached (" + DataConverter.toFormatedDuration(TIMEOUT_SHUTDOWN_MILLIS) + "). kill pending connections");
								for (SelectionKey sk : selector.keys()) {
									try {
										terminatedConnections++;
										sk.channel().close();
									} catch (Exception ignore) { }
								}

								break;
							}
						} while (getRegistered().size() > 0);

						isOpen = false;
						// wake up selector, so that isRunning-loop can be terminated
						selector.wakeup();

						if ((openConnections > 0) || (terminatedConnections > 0)) {
							if ((openConnections > 0) && (terminatedConnections > 0)) {
								LOG.info((openConnections - terminatedConnections) + " connections has been closed properly, "
										  + terminatedConnections + " connections has been terminate unclean");
							}
						}


						if (LOG.isLoggable(Level.FINE)) {
					        LOG.fine("dispatcher " + this.hashCode() + " has been closed (shutdown time = " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");
						}
					}
				};
				closer.setName("xDispatcherCloser");
				closer.start();
			}
		}
	}


	/**
	 * check if this dispatcher is open
	 * @return true, if the disptacher is open
	 */
	public final boolean isOpen() {
		return isOpen;
	}





	/**
	 * {@inheritDoc}
	 */
	protected long getNumberOfHandledRegistrations() {
		return handledRegistractions;
	}


	/**
	 * {@inheritDoc}
	 */
	protected long getNumberOfHandledReads() {
		return handledReads;
	}


	/**
	 * {@inheritDoc}
	 */
	protected long getNumberOfHandledWrites() {
		return handledWrites;
	}

	protected void resetStatistics() {
	    statisticsStartTime = System.currentTimeMillis();

		handledRegistractions = 0;
		handledReads = 0;
		handledWrites = 0;
	}

	protected long getStatisticsStartTime() {
		return statisticsStartTime;
	}


	private String printSelectionKeyValue(int key) {

		StringBuilder sb = new StringBuilder();

		if ((key & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
			sb.append("OP_ACCEPT, ");
		}

		if ((key & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT) {
			sb.append("OP_CONNECT, ");
		}

		if ((key & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
			sb.append("OP_WRITE, ");
		}

		if ((key & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
			sb.append("OP_READ, ");
		}

		String txt = sb.toString();
		txt = txt.trim();

		if (txt.length() > 0) {
			txt = txt.substring(0, txt.length() - 1);
		}

		return txt + " (" + key + ")";
	}
}
