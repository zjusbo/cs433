package org.xsocket.connection;




import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFrame;
import javax.swing.JTextField;



public final class BulkClient {

	
	public static void main(String... args) throws Exception {
		
		if (args.length < 4) {
			System.out.println("usage org.xsocket.stream.BulkClient <server> <port> <down|up> <dataSize>");
			System.exit(-1);
		}
		
		new BulkClient().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
		
		Monitor monitor = new Monitor();
	
		String server = args[0];
		int port = Integer.parseInt(args[1]);
		boolean isdown = args[2].equalsIgnoreCase("DOWN");
		long length = Long.parseLong(args[3]);


		//callByXsocket(monitor, server, port, length);
		callByNative(monitor, server, port, isdown, length);
    }


	

	private void callByNative(Monitor monitor, String server, int port, boolean isDown, long length) throws IOException, SocketTimeoutException, UnsupportedEncodingException {
		
		Socket socket = new Socket(server, port);
		InputStream is = socket.getInputStream();
		OutputStream os = socket.getOutputStream();
		
		LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
		
		byte[] data = QAUtil.generateByteArray((int) length);
		
		long start = 0;
		long end = 0;
		for (int i = 0; i < 1000000; i++) {		

			
			if (isDown) {
				pw.write(BulkDownloadServer.PREPARE_DOWNLOAD + length + "\r\n");
				pw.flush();
				String response = lnr.readLine();
				
				start = System.currentTimeMillis();
				pw.write(BulkDownloadServer.DOWNLOAD_REQUEST + "\r\n");
				pw.flush();
				lnr.readLine();
				end = System.currentTimeMillis();
								
			} else {
				pw.write(BulkUploadServer.UPLOAD_REQUEST + length + "\r\n");
				pw.flush();
				
				start = System.currentTimeMillis();
				os.write(data);	
				os.flush();
				String response = lnr.readLine();
				end = System.currentTimeMillis();
			}
			
			long elapsed = end - start;
			try {
				long rate = ((length * 1000) / elapsed);
				monitor.updateRate(rate);
			} catch (Exception ignore) { }

		}
	}

	
	
	
	
	private static final class Monitor extends JFrame {
		

		/**
		 * 
		 */
		private static final long serialVersionUID = 3198183689682443441L;
		private ArrayList<Long> rates = new ArrayList<Long>(); 
		private Timer timer = new Timer(true);
		
		private JTextField rateField = new JTextField("");
		private JTextField runningField = new JTextField("");
		
		private int runningCount = 0;
		
		
		public Monitor() {
			
			rateField.setEditable(false);
			runningField.setEditable(false);
			this.getContentPane().add(rateField, BorderLayout.CENTER);
			this.getContentPane().add(runningField, BorderLayout.SOUTH);
						
			
			this.setTitle("rate");
			this.setSize(200, 100);
			this.setVisible(true);
			this.setDefaultCloseOperation(EXIT_ON_CLOSE);
			
			
			TimerTask tt = new TimerTask() {
				@Override
				public void run() {
					
					List<Long> ratesCopy = null;
					synchronized (rates) {
						ratesCopy = (List<Long>) rates.clone();
						rates.clear();
					}
					
					long l = 0;
					for (Long rate : ratesCopy) {
						l += rate;
					}
					
					if (l > 0) {
						l = l / ratesCopy.size();
					}
					
					updateRateField(l);
				}
			};
			timer.schedule(tt, 3000, 3000);
		}
		
		public void updateRate(long rate) {
			synchronized (rates) {
				rates.add(rate);
			}
			
			
			runningCount++;
			if (runningCount > 45) {
				runningCount = 0;
			}
			
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < runningCount; i++) {
				sb.append(" ");
			}
			sb.append("*");
			runningField.setText(sb.toString());
		}
		
		private void updateRateField(long rate) {
			if (rate > 3000) {	
				if (rate > 3000000) {
					rateField.setText((rate / 1000000) + " mbyte/sec");		
				} else {
					rateField.setText((rate / 1000) + " kbyte/sec");
				}
					
			} else {
				rateField.setText(rate + " bits/sec");
			}
		}
	}
	
}
