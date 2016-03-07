package org.xsocket.stream;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

final class Statistic {

	private static final int SECTION_TIMERANGE = 5 * 60 * 1000; 
	
	private final ArrayList<Integer> times = new ArrayList<Integer>();
	private ArrayList<Integer> tempTimes = new ArrayList<Integer>();
	private long lastPrint = System.currentTimeMillis();
	
	private long lastSection = System.currentTimeMillis();

	private Timer timer = new Timer(true);
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
	
	private boolean firstPrint = true;
	
	public Statistic() {
		try {
			TimerTask timerTask = new TimerTask() {
				@Override
				public void run() {
					System.out.println(print());
				}
			};
			
			timer.schedule(timerTask, 5000, 5000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private String printHeader() {
		return "time; calls; throughput; min; max; average; median; p75; p90; p95; p99";
	}

	
	
	public synchronized void addValue(int value) {
		times.add(value);
		tempTimes.add(value);
	}
	
	
	@SuppressWarnings("unchecked")
	String print() {
		StringBuilder sb = new StringBuilder();
		
		ArrayList<Integer> copy = (ArrayList<Integer>) times.clone();
		Collections.sort(copy);
		
		int sum = 0;
		for (Integer i : copy) {
			sum += i;
		}

		if (firstPrint) {
			firstPrint = false;
			System.out.println("\r\n" + printHeader());
		}
		
		
		sb.append(dateFormat.format(new Date())  + "; ");

		sb.append(copy.size()  + "; ");

		ArrayList<Integer> tempTimesCopy = tempTimes;
		tempTimes = new ArrayList<Integer>();
		long elapsed = System.currentTimeMillis() - lastPrint;
		lastPrint = System.currentTimeMillis();
		sb.append(((tempTimesCopy.size() * 1000) / elapsed) + "; ");
		
		
		if (copy.size() > 0) {
			sb.append(copy.get(0) + "; ");
			sb.append(copy.get(copy.size() - 1) + "; ");
			sb.append((sum / copy.size()) + "; ");
			sb.append(copy.get(copy.size() / 2) + "; ");
			sb.append(copy.get((int) (copy.size() * 0.75)) + "; ");
			sb.append(copy.get((int) (copy.size() * 0.9)) + "; ");
			sb.append(copy.get((int) (copy.size() * 0.95)) + "; ");
			sb.append(copy.get((int) (copy.size() * 0.99)));
		}

		if (System.currentTimeMillis() > (lastSection + SECTION_TIMERANGE)) {
			lastSection = System.currentTimeMillis();
			times.clear();
			sb.append("\r\n\r\n");
			sb.append(printHeader());
		}
		
		return sb.toString();
	}
	
}

