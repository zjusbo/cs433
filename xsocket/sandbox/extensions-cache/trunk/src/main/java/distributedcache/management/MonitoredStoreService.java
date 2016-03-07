package distributedcache.management;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.BufferUnderflowException;

import javax.management.ObjectName;

import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.Resource;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IServerContext;


import distributedcache.StoreService;

public class MonitoredStoreService  implements IDataHandler, ILifeCycle {
	
	private StoreService delegee = null;
	private ObjectName objectName = null;
 
	@Resource
	private IServerContext ctx = null;

	
	public MonitoredStoreService(StoreService delegee) {
		this.delegee = delegee;
	}
	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return delegee.onData(connection);
	}

	
	public void onInit() {
		injectContext(delegee);
		delegee.onInit();
		
		try {
			objectName = new ObjectName("domain" + ":type=StoreService,name=" + this.hashCode());
			ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(delegee), objectName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	 
	public void onDestroy() {
		delegee.onDestroy();
		
		try {
			objectName = new ObjectName("domain" + ":type=StoreService,name=" + this.hashCode());
			ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	private void injectContext(IHandler hdl) {
		IServerContext ctx = null;
		Field[] fields = hdl.getClass().getDeclaredFields();
		for (Field field : fields) {
			if ((field.getType() == IServerContext.class) && (field.getAnnotation(Resource.class) != null)) {
				field.setAccessible(true);
				try {
					if (ctx == null) {
						ctx = this.ctx;
					}
					field.set(hdl, ctx);
				} catch (IllegalAccessException iae) {
					iae.printStackTrace();
				}				
			}
		}
	}

}
