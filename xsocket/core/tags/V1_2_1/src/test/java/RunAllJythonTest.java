


import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


import org.junit.Assert;
import org.junit.Test;
import org.python.util.jython;
import org.xsocket.DebugPrintStream;
import org.xsocket.QAUtil;



/**
*
* @author grro@xsocket.org
*/
public final class RunAllJythonTest  {

	private String basepath = null;

	 public RunAllJythonTest() {
		 basepath = getClass().getResource("").getFile();
	 }


	 private RunAllJythonTest(String basepath) {
		 this.basepath = basepath;
	 }


	 public static void main(String... args) throws Exception {
		 new RunAllJythonTest(args[0]).testAllScripts();
	 }


	@Test
	public void testAllScripts() throws Exception {

		//QAUtil.setLogLevel(Level.FINE);

		PrintStream consoleOut = System.out;
		DebugPrintStream debugPrintStream = new DebugPrintStream(consoleOut);
		System.setOut(debugPrintStream);

		List<String> scriptFiles = new ArrayList<String>();

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
		System.out.println("all " + scriptFiles.size() + " jython test passed");
	}


	public void runScript(String script) throws Exception {
		jython.main(new String[] { script });
	}


	private void scanSrcipts(File dir, List<String> scripts) {
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				scanSrcipts(file, scripts);
			} else {
				if (file.getName().endsWith(".py")) {
					scripts.add(file.getAbsolutePath());
				}
			}
		}
	}
}
