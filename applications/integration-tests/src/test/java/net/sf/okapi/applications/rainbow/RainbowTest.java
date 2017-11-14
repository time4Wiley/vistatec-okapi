
package net.sf.okapi.applications.rainbow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import net.sf.okapi.common.FileCompare;
import net.sf.okapi.common.StreamGobbler;
import net.sf.okapi.common.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RainbowTest {
	
	private String[] javaRainbow;
	private String root;
	private File rootAsFile;
	private FileCompare fc = new FileCompare();

	@Before
	public void setUp () throws URISyntaxException {
		File file = new File(getClass().getResource("/htmltest.html").toURI());
		root = Util.getDirectoryName(file.getAbsolutePath());
		rootAsFile = new File(root);

		String distDir;
		String osName = System.getProperty("os.name");
		if ( osName.contains("OS X") ) { // Macintosh case
			if ( System.getProperty("os.arch").equals("x86_64") || System.getProperty("os.arch").equals("amd64")) {
                distDir = "dist_cocoa-macosx-x86_64";
            } else {
                distDir = "dist_cocoa-macosx";
            }
			//TODO: How to detect carbon vs cocoa?
		}
		else if ( osName.startsWith("Windows") ) { // Windows case
			if ( System.getProperty("os.arch").equals("x86_64") || System.getProperty("os.arch").equals("amd64")) {
                distDir = "dist_win32-x86_64";
            } else {
                distDir = "dist_win32-x86";
            }
		}
		else { // Assumes Unix or Linux
			if ( System.getProperty("os.arch").equals("x86_64") || System.getProperty("os.arch").equals("amd64")) {
				distDir = "dist_gtk2-linux-x86_64";
			}
			else {
				distDir = "dist_gtk2-linux-x86";
			}
		}
		
		// Set the path for the jar
		String libDir = Util.getDirectoryName(root); // Go up one dir
		libDir = Util.getDirectoryName(libDir); // Go up one dir
		libDir = Util.getDirectoryName(libDir); // Go up one dir
		libDir = Util.getDirectoryName(libDir); // Go up one dir
		libDir += String.format("%sdeployment%smaven%s%s%slib%s",
			File.separator, File.separator, File.separator, distDir, File.separator, File.separator);
		
		if ( osName.startsWith("Windows") ) { // Windows case
			javaRainbow = new String[] { "java", "-jar", libDir + "rainbow.jar" };

		} else if ( osName.contains("OS X") ) { // Mac case
			// OS X requires -XstartOnFirstThread for GUI applications
			javaRainbow = new String[] { "java", "-XstartOnFirstThread", "-jar", libDir + "rainbow.jar" };
		}
		else { // Assumes Unix or Linux
			javaRainbow = new String[] { "java", "-jar", libDir + "rainbow.jar" };
		}
	}

	@Test
	public void testRewriting() throws IOException, InterruptedException {
		assertTrue(deleteOutputFile("potest.rbout.po"));
		assertEquals(0, runRainbow("-np -p pipelines/textrewriting.rnb -pln pipelines/textrewriting.pln"));
		assertTrue("File different from gold",
			compareWithGoldFile("pipelines/potest.rbout.po"));
	}

	@Test
	public void testPipeline01 () throws IOException, InterruptedException {
		// Delete previous output
		assertTrue(deleteOutputFile("pipelines/input01.out.html"));
		assertEquals(0, runRainbow("-np -p pipelines/test01.rnb -pln pipelines/test01.pln"));
		assertTrue("File different from gold",
			compareWithGoldFile("pipelines/input01.out.html"));
	}

	/*
	 For some reason the Vignette filter is never called creating an empty tkit and no vignetteTest01.out.xml file
	 Has the mime type mapping changed? Vignette filter unit tests all pass so I assume everything is working
	 but some glue code is not - JEH 9/25/2012
	@Test
	public void testVignetteFilter () throws IOException, InterruptedException {
		// Delete previous output
		assertTrue(deleteOutputFile("pipelines/vignettePack1/done/vignetteTest01.out.xml"));
		assertEquals(0, runRainbow("-np -p pipelines/vignetteTestPart1.rnb -x oku_extraction"));
		assertEquals(0, runRainbow("-np -p pipelines/vignetteTestPart2.rnb -x oku_merging"));
		assertTrue("File different from gold",
			compareWithGoldFile("pipelines/vignettePack1/done/vignetteTest01.out.xml", "pipelines/vignetteTest01.out.xml"));
	}
	*/

	@Test
	public void testTMs () throws IOException, InterruptedException {
		// Delete for all passes
		// Delete output of pass 1
		assertTrue(deleteOutputFile("pipelines/tm/out1.tmx"));
		assertTrue(deleteOutputFile("pipelines/tm/test01.out1.html"));
		// Delete output of pass 2
		assertTrue(deleteOutputDir("pipelines/tm/out2.pentm", true));
		// Delete output of pass 3
		assertTrue(deleteOutputFile("pipelines/tm/test01.out3.html"));
		// Delete output of pass 4
		assertTrue(deleteOutputFile("pipelines/tm/out4.h2.db")); //data.db"));
		//assertTrue(deleteOutputFile("pipelines/tm/out4.index.db"));
		// Delete output of pass 5
		assertTrue(deleteOutputFile("pipelines/tm/test01.out5.html"));
		
		// Pseudo translate and create TMX and pseudo-translated base output
		assertEquals(0, runRainbow("-np -p pipelines/tm/pass1.rnb -pln pipelines/tm/pass1.pln -pd pipelines/tm"));
		// Create Pensieve TM
		assertEquals(0, runRainbow("-np -p pipelines/tm/pass2.rnb -pln pipelines/tm/pass2.pln -pd pipelines/tm"));
		// Leverage from the Pensieve TM
		assertEquals(0, runRainbow("-np -p pipelines/tm/pass3.rnb -pln pipelines/tm/pass3.pln -pd pipelines/tm"));
		// Create SimpleTM TM
		assertEquals(0, runRainbow("-np -p pipelines/tm/pass4.rnb -pln pipelines/tm/pass4.pln -pd pipelines/tm"));
		// Leverage from SimpleTM TM
		assertEquals(0, runRainbow("-np -p pipelines/tm/pass5.rnb -pln pipelines/tm/pass5.pln -pd pipelines/tm"));

		// Compare leveraged results with base output
		String basePath = root + File.separator + "pipelines/tm/Test01.out1.html";
		String outputPath = root + File.separator + "pipelines/tm/Test01.out3.html";
		assertTrue("Pensive leveraged output is different", fc.filesExactlyTheSame(outputPath, basePath));
		outputPath = root + File.separator + "pipelines/tm/Test01.out5.html";
		assertTrue("Pensive leveraged output is different", fc.filesExactlyTheSame(outputPath, basePath));
	}
	

	@Test
	public void testDiffLeverage1 () throws IOException, InterruptedException {
		// Delete previous output
		assertTrue(deleteOutputFile("pipelines/diffleverage/myFile_en_new.out.html"));
		assertEquals(0, runRainbow("-np -p pipelines/diffleverage/diffleverage1.rnb -pln pipelines/diffleverage/diffleverage1.pln"));
		assertTrue("File different from gold",
			compareWithGoldFile("pipelines/diffleverage/myFile_en_new.out.html"));
	}

	@Test
	public void testLeverage () throws IOException, InterruptedException {
		// Delete previous output
		assertTrue(deleteOutputFile("pipelines/leverage/test01.out.html"));
		assertTrue(deleteOutputFile("pipelines/leverage/test01.out.po"));
		assertTrue(deleteOutputFile("pipelines/leverage/simpleTM.h2.db"));
		assertTrue(deleteOutputDir("pipelines/leverage/pensieveTM.pentm", true));
		assertTrue(deleteOutputFile("pipelines/leverage/output1.tmx"));
		
		// Create the SimpleTM TM
		assertEquals(0, runRainbow("-np -p pipelines/leverage/createSimpleTM.rnb -pln pipelines/leverage/createSimpleTM.pln"));
		// Create the Pensieve TM
		assertEquals(0, runRainbow("-np -p pipelines/leverage/createPensieveTM.rnb -pln pipelines/leverage/createPensieveTM.pln"));
		// Execute first preparation
		assertEquals(0, runRainbow("-np -p pipelines/leverage/prepare1.rnb -pln pipelines/leverage/prepare1.pln"));
		assertTrue("test01.out.html different from gold",
			compareWithGoldFile("pipelines/leverage/test01.out.html"));
		assertTrue("test01.out.po different from gold",
			compareWithGoldFile("pipelines/leverage/test01.out.po"));
		assertTrue("output1.tmx different from gold",
			compareWithGoldFilePerLine("pipelines/leverage/output1.tmx", "UTF-8"));
	}

	@Test
	public void testEncodingConversion () throws IOException, InterruptedException {
		// Delete previous output
		assertTrue(deleteOutputFile("pipelines/testForEncoding1.out.html"));
		assertTrue(deleteOutputFile("pipelines/testForEncoding2.out.html"));
		assertEquals(0, runRainbow("-np -p pipelines/encodingConversion.rnb -pln pipelines/encodingConversion.pln"));
		assertTrue("File different from gold", compareWithGoldFile("pipelines/testForEncoding1.out.html"));
		assertTrue("File different from gold", compareWithGoldFile("pipelines/testForEncoding2.out.html"));
	}

	@Test
	public void testBOMLBConversion () throws IOException, InterruptedException {
		// Delete previous output
		assertTrue(deleteOutputFile("pipelines/bomlbConversion1_utf8.out.txt"));
		assertTrue(deleteOutputFile("pipelines/bomlbConversion2_utf16BE.out.txt"));
		assertEquals(0, runRainbow("-np -p pipelines/bomlbConversion.rnb -pln pipelines/bomlbConversion.pln"));
		assertTrue("File different from gold", compareWithGoldFile("pipelines/bomlbConversion1_utf8.out.txt"));
		assertTrue("File different from gold", compareWithGoldFile("pipelines/bomlbConversion2_utf16BE.out.txt"));
	}

	@Test
	public void testRTFConversion () throws IOException, InterruptedException {
		// Delete previous output
		assertTrue(deleteOutputFile("pipelines/rtfConversion1.out.rtf"));
		assertEquals(0, runRainbow("-np -p pipelines/rtfConversion.rnb -pln pipelines/rtfConversion.pln"));
		assertTrue("File different from gold", compareWithGoldFile("pipelines/rtfConversion1.out.rtf"));
	}

	private boolean compareWithGoldFile (String outputBase) {
    	String outputPath = root + File.separator + outputBase;
    	String goldPath = root + File.separator + "gold" + File.separator + outputBase; 
    	return fc.filesExactlyTheSame(outputPath, goldPath);
    }

	private boolean compareWithGoldFilePerLine (String outputBase,
		String encoding) throws FileNotFoundException
	{
    	String outputPath = root + File.separator + outputBase;
    	String goldPath = root + File.separator + "gold" + File.separator + outputBase; 
    	return fc.compareFilesPerLines(outputPath, goldPath, encoding);
    }

//    private boolean compareWithGoldFile (String outputBase,
//    	String goldBase)
//    {
//		String outputPath = root + File.separator + outputBase;
//		String goldPath = root + File.separator + "gold" + File.separator + goldBase;
//		return fc.filesExactlyTheSame(outputPath, goldPath);
//	}        
    
    private boolean deleteOutputFile (String filename) {
    	File f = new File(root + File.separator + filename);
    	if ( f.exists() ) {
    		return f.delete();
    	}
    	else return true;
    }
    
    public boolean deleteOutputDir (String dirname, boolean relative) {
    	File d;
    	if ( relative ) d = new File(root + File.separator + dirname);
    	else d = new File(dirname);
    	if ( d.isDirectory() ) {
    		String[] children = d.list();
    		for ( int i=0; i<children.length; i++ ) {
    			boolean success = deleteOutputDir(d.getAbsolutePath() + File.separator + children[i], false);
    			if ( !success ) {
    				return false;
    			}
    		}
    	}
    	if ( d.exists() ) return d.delete();
    	else return true;
    }
    
    private int runRainbow (String extraArgs) throws IOException, InterruptedException {
    	List<String> args = new ArrayList<String>();
    	for (String arg : javaRainbow) {
    		args.add(arg);
    	}
    	for (String arg : extraArgs.split("\\s+")) {
    		args.add(arg);
    	}
    	Process p = Runtime.getRuntime().exec(args.toArray(new String[0]),
    		null, rootAsFile);
    	StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "err");            
    	StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "out");
    	errorGobbler.start();
    	outputGobbler.start();
    	p.waitFor();
    	return p.exitValue();
    }

}
