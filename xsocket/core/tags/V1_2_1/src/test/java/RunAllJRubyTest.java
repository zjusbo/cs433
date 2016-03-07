


import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DebugPrintStream;



/**
*
* @author grro@xsocket.org
*/
public final class RunAllJRubyTest  {

	private String basepath = null;

	 public RunAllJRubyTest() {
		 basepath = getClass().getResource("").getFile();
	 }


	 private RunAllJRubyTest(String basepath) {
		 this.basepath = basepath;
	 }


	 public static void main(String... args) throws Exception {
		 new RunAllJRubyTest(args[0]).testAllScripts();
	 }


	@Test
	public void testAllScripts() throws Exception {

		List<String> scriptFiles = new ArrayList<String>();

		//QAUtil.setLogLevel(Level.FINE);

		PrintStream consoleOut = System.out;
		DebugPrintStream debugPrintStream = new DebugPrintStream(consoleOut);
		System.setOut(debugPrintStream);


		scanSrcipts(new File(basepath), scriptFiles);

		if (scriptFiles.isEmpty()) {
			System.out.println("no jruby scripts found");
			Assert.fail("non scripts found");
		}

		for (String scriptFile : scriptFiles) {
			System.out.println("performing script " + scriptFile);

			debugPrintStream.clear();
			runScript(scriptFile);


			String out = new String(debugPrintStream.getData(), "UTF-8");
			Assert.assertTrue("got " +  out + " instead of OK", out.startsWith("OK"));
			System.out.println("passed");
		}

		System.setOut(consoleOut);
		System.out.println("all " + scriptFiles.size() + " jruby test passed");
	}


	public void runScript(String script) throws Exception {
		org.jruby.Main.main(new String[] { script });
	}


	private void scanSrcipts(File dir, List<String> scripts) {
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				scanSrcipts(file, scripts);
			} else {
				if (file.getName().endsWith(".rb")) {
					scripts.add(file.getAbsolutePath());
				}
			}
		}
	}
}
