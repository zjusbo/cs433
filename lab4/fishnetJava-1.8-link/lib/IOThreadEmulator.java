public class IOThreadEmulator extends IOThread {

    public static final int ID = 2;
    private MultiplexIO multiplexIO;

    public IOThreadEmulator(MultiplexIO multiplexIO) {
	super();
	this.multiplexIO = multiplexIO;
    }

    protected synchronized void addLine(String line) {
	this.inputLines.add(line);
        /*
         * Apr. 1, 2006
         * Hao Wang
         *
         * Eliminate the use of a polling thread to improve performance
         */
        /*
         * try {
         *     this.multiplexIO.write(ID);
         * }catch(IOException e) {
         *     System.out.println("IOException occured in IOThreadEmulator while writing to MultiplexIO. Exception: " + e);
         * }
         */
        this.multiplexIO.write(ID);
    }
}
