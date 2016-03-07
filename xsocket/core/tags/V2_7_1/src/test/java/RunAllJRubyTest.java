


import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.List;


//import org.jruby.Ruby;
import org.junit.Test;



/**
*
* @author grro@xsocket.org
*/
public final class RunAllJRubyTest  {

    private String basepath = null;

     public RunAllJRubyTest() {
         this(RunAllJRubyTest.class.getResource("").getFile());
     }


     private RunAllJRubyTest(String basepath) {
    	 this.basepath = new File(basepath).getParentFile().getAbsoluteFile() + File.separator + "test-classes";
     }


     public static void main(String... args) throws Exception {
         new RunAllJRubyTest(args[0]).testAllScripts();
     }


    @Test
    public void testAllScripts() throws Exception {
/*
        List<String> scriptFiles = new ArrayList<String>();


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

            try {
            	runScript(new File(scriptFile));

                String out = new String(debugPrintStream.getData(), "UTF-8");
                Assert.assertTrue("got " +  out + " instead of OK", out.startsWith("OK"));
                System.out.println("passed");
                
            } catch (Exception e) {
            	e.printStackTrace();            	
            	System.out.println("error occured by performing script " + scriptFile + " " + e.toString());
                Assert.fail(e.toString());
            }
        }

        System.setOut(consoleOut);
        System.out.println("all " + scriptFiles.size() + " jruby test passed");*/
    }


    public void runScript(File scriptFile) throws Exception {
		StringBuilder sb = new StringBuilder();
		LineNumberReader lnr = new LineNumberReader(new FileReader(scriptFile));
		
		String line = null;
		do {
			line = lnr.readLine();
			if (line != null) {
				sb.append(line + "\r\n");
			}
		} while (line != null);

//		Ruby runtime = Ruby.getDefaultInstance();
 //       runtime.executeScript(sb.toString(), scriptFile.getAbsolutePath());
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
