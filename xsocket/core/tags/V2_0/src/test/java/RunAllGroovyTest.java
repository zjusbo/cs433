

import groovy.lang.GroovyShell;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DebugPrintStream;
import org.xsocket.QAUtil;


/**
*
* @author grro@xsocket.org
*/
public final class RunAllGroovyTest  {

	private String basepath = null;

	 public RunAllGroovyTest() {
		 basepath = getClass().getResource("").getFile();
	 }


	 private RunAllGroovyTest(String basepath) {
		 this.basepath = basepath;
	 }


	 public static void main(String... args) throws Exception {
		 new RunAllGroovyTest(args[0]).testAllScripts();
	 }


	@Test
	public void testAllScripts() throws Exception {


		PrintStream consoleOut = System.out;
		DebugPrintStream debugPrintStream = new DebugPrintStream(consoleOut);
		System.setOut(debugPrintStream);


		List<File> scriptFiles = new ArrayList<File>();

		scanSrcipts(new File(basepath), scriptFiles);

		if (scriptFiles.isEmpty()) {
			System.out.println("no groovy scripts found");
			Assert.fail("non scripts found");
		}

		for (File scriptFile : scriptFiles) {
			System.out.println("performing script " + scriptFile);

			debugPrintStream.clear();
			runScript(scriptFile);

			String out = new String(debugPrintStream.getData(), "UTF-8");
			if (out.startsWith("OK")) {
				System.out.println("passed");
				
			} else {
				System.out.println("failed got " +  out + " instead of OK");
				Assert.fail("failed got " +  out + " instead of OK");
			}
		}


		System.setOut(consoleOut);
		System.out.println("all " + scriptFiles.size() + " groovy test passed");
	}


	public void runScript(File script) throws Exception {
		try {
			GroovyShell gs = new GroovyShell();
			gs.evaluate(script);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}


	private void scanSrcipts(File dir, List<File> scripts) {
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				scanSrcipts(file, scripts);
			} else {
				if (file.getName().endsWith(".groovy")) {
					scripts.add(file);
				}
			}
		}
	}
}
