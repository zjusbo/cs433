package proj;

public class Debug {
	private static boolean DEBUG = true;
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
