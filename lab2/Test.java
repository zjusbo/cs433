public class Test {
	public static void main(String[] args) throws Exception{
		System.out.println(Character.toString ((char) 128).getBytes("US-ASCII").length);
		System.out.println(Character.toString ((char) 128).getBytes("UTF-8").length);

	}
}