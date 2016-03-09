package utility;
public class Debug {

    private static boolean DEBUG = true;
    private static int level = 2; // verbose level
    
    public static void DEBUG(String s, int level) {
    	if (DEBUG && level <= Debug.level)
    	    System.out.println(s);
        }
    public static void DEBUG(String s) {
		DEBUG(s, 3);
    }
}
