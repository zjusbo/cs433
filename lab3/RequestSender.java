

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;

public class RequestSender implements Runnable{
	private SocketFactory clientSocketFactory;
	private int pid;
	private PerformanceData performanceData;
	private int verbose = 0;
	private volatile Thread thisThread;
	private ArrayList<HTTPRequest> requestList;
	public RequestSender(int pid, SocketFactory clientSocketFactory, ArrayList<HTTPRequest> requestList, PerformanceData pd){
		this.pid = pid;
		this.clientSocketFactory = clientSocketFactory;
		this.requestList = requestList;
		this.performanceData = pd;
	}
	public void setVerbose(int v){
		this.verbose = v;
	}
	
	// stop current thread
	public void stop(){
		Thread thread = this.thisThread;
		this.thisThread = null;
		// wake up from blocking operation
		if(thread != null)
			thread.interrupt();
	}
	public void run(){
		long total_recv_byte_num = 0;
		long total_recv_packet_num = 0;
		long total_response_milli_seconds = 0;
		this.thisThread = Thread.currentThread();
		
		try{
			// while current thread still need to run
			while(this.thisThread != null){				
				for(HTTPRequest req : this.requestList){
					if(this.thisThread == null) break; // stop running
					Socket socket = this.clientSocketFactory.getSocket();
					long recv_byte_num = 0;
					// write to server
					print("Sending request: " + req.getURL() + " to " + socket.getInetAddress().getHostName(), 1);
					DataOutputStream outToServer 
					   = new DataOutputStream(socket.getOutputStream());
					outToServer.write(req.getBytes());
					long startTime = System.currentTimeMillis();
					print(req, 2);
					// recv response
					// create read stream and receive from server
					print("Recieving response from: " + socket.getInetAddress(), 1);
					BufferedReader inFromServer 
					 = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					String sentenceFromServer = inFromServer.readLine();
					long responseTime = System.currentTimeMillis() - startTime;
					while(sentenceFromServer != null){
						recv_byte_num += sentenceFromServer.length();
						sentenceFromServer = inFromServer.readLine();
						print(sentenceFromServer, 2);
					}
					print("Response time: " + responseTime, 1);
					print("Recv bytes: " + recv_byte_num, 1);
					total_recv_byte_num += recv_byte_num;
					total_recv_packet_num++;
					total_response_milli_seconds += responseTime;
					socket.close();
				}
			}
		}catch(Exception e){
			System.err.println(e.getStackTrace());
			
		}finally{
			
			// collect data and return
			synchronized(this.performanceData){
				this.performanceData.num_bytes += total_recv_byte_num;
				this.performanceData.num_files += total_recv_packet_num;
				this.performanceData.response_time += total_response_milli_seconds;
			}
		}
	}
	private void print(Object s, int level){
		if(this.verbose >= level)
			System.out.println("Thread " + this.pid + ": " + s);
	}
}
