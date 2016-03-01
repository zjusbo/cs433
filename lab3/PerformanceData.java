

class PerformanceData{
	public long num_bytes;
	public long num_files;
	public long response_time;
	
	public PerformanceData(){}
	
	public String toString(){
		return "num_bytes = " + this.num_bytes + "\n" + 
				"num_files" + this.num_files + "\n" + 
				"response_time" + this.response_time;
	}
}
