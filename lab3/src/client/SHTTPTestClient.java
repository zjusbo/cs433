package client;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Scanner;

import utility.SocketFactory;

import utility.HTTPRequest;
import utility.PerformanceData;

public class SHTTPTestClient {
	public static void main(String args[]) throws IOException, InterruptedException{
		ClientArgument config = ClientArgument.parse(args);
		if(config == null){
			// argument incorrect
			return ;
		}
		
		System.out.println(config);
		if(config.filename == null){
			System.err.println("filename required");
			return ;
		}
		ArrayList<String> filenameList = extractFiles(config.filename);
		if(filenameList.isEmpty()){
			System.err.println("no content in " + config.filename);
			return ;
		}
		// prepare for socket config
		InetAddress serverIPAddress = InetAddress.getByName(config.server);
		ArrayList<HTTPRequest> requestList = new ArrayList<HTTPRequest>();
		ArrayList<Thread> threadList = new ArrayList<Thread>();
		ArrayList<RequestSender> runnableList = new ArrayList<RequestSender>();
		for(String url : filenameList){
			HTTPRequest request = new HTTPRequest(url, config.host);
			requestList.add(request);
		}
		
		SocketFactory sf = new SocketFactory(serverIPAddress, config.port);
		PerformanceData performanceData = new PerformanceData();
		// create threads, feed them with sockets
		
		for(int i = 1; i <= config.parallel; i++){
			RequestSender rs = new RequestSender(i, sf, requestList, performanceData);
			rs.setVerbose(config.verbose);
			runnableList.add(rs);
			threadList.add(new Thread(rs));
		}
		
		long startTime = System.currentTimeMillis();
		// start all threads
		for(Thread r: threadList){
			r.start();
		}
		
		try{
			Thread.sleep(config.T * 1000); // sleep T seconds
			for(RequestSender rs: runnableList){
				rs.stop();
			}
			// send stop signal to all thread
			for(Thread r: threadList){
				r.join();
			}
		}catch(InterruptedException e){
			System.err.println("Caught Interrupt.");
			// send stop signal to all thread
			for(RequestSender rs: runnableList){
				rs.stop();
			}
			return ;
		}		
		long endTime = System.currentTimeMillis();
		String report = getStatisticData(performanceData, startTime, endTime);
		System.out.println(report);
	}
	
	static ArrayList<String> extractFiles(String filename) throws FileNotFoundException{
		ArrayList<String> fileList = new ArrayList<String>();
		Scanner scanner = new Scanner(new FileReader(filename));
		while(scanner.hasNext()){
			fileList.add(scanner.next());
		}
		scanner.close();
		return fileList;
	}
	
	private static String getStatisticData(PerformanceData performanceData, long startTime, long endTime){
		double interval = (double)(endTime - startTime) / 1000;
		double transaction_throughput = performanceData.num_files / interval;
		double data_throughput = performanceData.num_bytes / interval;
		double avg_response_time = (double)performanceData.response_time / performanceData.num_files;
		String s;
		s = "Performance:\n";
		s += "\tTransaction throughput: " + transaction_throughput + " files/s\n";
		s += "\tData throughput: " + data_throughput / 1024 + " kB/s\n";
		s += "\tAvg response time: " + avg_response_time + " ms"; 
		return s;
	}
}