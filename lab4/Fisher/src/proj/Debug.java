package proj;

public class Debug {
	private static boolean DEBUG = false;
	public static void print(String s){
		if(DEBUG){
			System.out.print(s);
		}
	}
	public static void println(String s){
		if(DEBUG){
			System.out.println(s);
		}
	}
}
