package com.nq.auto;

import android.content.res.AXmlResourceParser;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 
 * @author jiakui
 *
 */
public class APKManger {
	private static String jarsignerpath;
	private static String zipalignpath;
	private static final float[] RADIX_MULTS = { 0.0039063F, 3.051758E-005F,
			1.192093E-007F, 4.656613E-010F };

	private static final String[] DIMENSION_UNITS = { "px", "dip", "sp", "pt",
			"in", "mm", "", "" };

	private static final String[] FRACTION_UNITS = { "%", "%p", "", "", "", "",
			"", "" };

	public void checkEnvironment() {
		String javahome = System.getenv("JAVA_HOME");
		String androidhome = System.getenv("ANDROID_HOME");
		if (javahome == null)
			throw new RuntimeException(
					"JAVA_HOME not set. Please install Java SDK and set the JAVA_HOME environment variable");
		if (androidhome == null)
			throw new RuntimeException(
					"ANDROID_HOME not set. Please install Android SDK and set the ANDROID_HOME environment variable");
		jarsignerpath = new File(javahome).getAbsolutePath() + "/bin/jarsigner";
		zipalignpath = new File(androidhome).getAbsolutePath()
				+ "/tools/zipalign";
	}

	public String join(String[] cmd) {
		StringBuilder sb = new StringBuilder();
		String[] arrayOfString = cmd;
		int j = cmd.length;
		for (int i = 0; i < j; i++) {
			String s = arrayOfString[i];
			sb.append(s + " ");
		}
		return sb.toString();
	}

	public void zipAlign(String inputFile, String outputFile) throws Exception {
		String[] cmdLine = { zipalignpath, "-f", "4", inputFile, outputFile };
		System.out.println("Running zipalign\nCommand line: " + join(cmdLine));
		Process proc = Runtime.getRuntime().exec(cmdLine);
		proc.waitFor();
		InputStream err = proc.getErrorStream();
		InputStream in = proc.getInputStream();
		System.out.println("zipalign finished with following output:");
		while (err.available() > 0)
			System.out.print((char) err.read());
		while (in.available() > 0)
			System.out.print((char) in.read());
	}

	public void signWithDebugKey(String inputFile, String path)
			throws Exception {
		String userDir = System.getProperty("user.home");
		String debugKeyStore = userDir + "/.android/debug.keystore";
		String[] cmdLine = { jarsignerpath, "-sigalg", "MD5withRSA",
				"-digestalg", "SHA1", "-keystore", path, "-storepass",
				"android", "-keypass", "android", inputFile, "androiddebugkey" };
		System.out.println("Running jarsigner\nCommand line: " + join(cmdLine));
		Process proc = Runtime.getRuntime().exec(cmdLine);
		proc.waitFor();
		InputStream err = proc.getErrorStream();
		InputStream in = proc.getInputStream();
		System.out.println("jarsigner finished with following output:");
		while (err.available() > 0)
			System.out.print((char) err.read());
		while (in.available() > 0)
			System.out.print((char) in.read());
	}

	public String[] stripSigning(String inputFile, String outputFile)
			throws Exception {
		ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile));
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(
				outputFile));
		ZipEntry entry = null;
		String[] resultString = (String[]) null;
		while ((entry = zis.getNextEntry()) != null) {
			if (!entry.getName().contains("META-INF")) {
				zos.putNextEntry(new ZipEntry(entry.getName()));

				ByteBuffer bb = ByteBuffer.allocate(500000);
				byte[] buffer = new byte[2048];
				int size;
				while ((size = zis.read(buffer, 0, buffer.length)) != -1) {

					zos.write(buffer, 0, size);
					if (entry.getName().endsWith("AndroidManifest.xml")) {
						bb.put(buffer, 0, size);
					}
				}
				zos.flush();
				zos.closeEntry();
				if (bb.position() > 0) {
					buffer = new byte[bb.position()];
					bb.rewind();
					bb.get(buffer);
					DocumentBuilderFactory docFactory = DocumentBuilderFactory
							.newInstance();
					docFactory.setNamespaceAware(true);
					docFactory.setIgnoringComments(true);
					docFactory.setIgnoringElementContentWhitespace(true);

					DocumentBuilder builder = docFactory.newDocumentBuilder();
					String packageName = "";
					String mainActivity = "";
					String docStr = AXMLToXML(buffer).replaceAll("\n", "");
					Pattern pattern = Pattern
							.compile("<activity.*?android:name=\"(.*?)\".*?</activity>");
					Matcher m = pattern.matcher(docStr);
					while (m.find()) {
						String result = m.group();
						if (result.contains("android.intent.action.MAIN")) {
							if (result
									.contains("android.intent.category.LAUNCHER")) {
								mainActivity = m.group(1);
							}
						}
					}

					pattern = Pattern.compile("<manifest.*?package=\"(.*?)\"");
					m = pattern.matcher(docStr);
					if (m.find()) {
						packageName = m.group(1);
					}
					mainActivity = mainActivity.replaceAll(packageName, "");
					resultString = new String[] { packageName, mainActivity };
				}
			}
		}
		zis.close();
		zos.close();
		return resultString;
	}

	public String[] resign(String in, String out, String path) throws Exception {
		APKManger rl = new APKManger();
		File f = File.createTempFile("resigner", ".apk");
		String[] result = stripSigning(in, f.getAbsolutePath());
		signWithDebugKey(f.getAbsolutePath(), path);
		zipAlign(f.getAbsolutePath(), out);
		return result;
	}

	private String getNamespacePrefix(String prefix) {
		if ((prefix == null) || (prefix.length() == 0)) {
			return "";
		}
		return prefix + ":";
	}

	private String getAttributeValue(AXmlResourceParser parser, int index) {
		int type = parser.getAttributeValueType(index);
		int data = parser.getAttributeValueData(index);
		if (type == 3) {
			return parser.getAttributeValue(index);
		}
		if (type == 2) {
			return String.format("?%s%08X", new Object[] { getPackage(data),
					Integer.valueOf(data) });
		}
		if (type == 1) {
			return String.format("@%s%08X", new Object[] { getPackage(data),
					Integer.valueOf(data) });
		}
		if (type == 4) {
			return String.valueOf(Float.intBitsToFloat(data));
		}
		if (type == 17) {
			return String.format("0x%08X",
					new Object[] { Integer.valueOf(data) });
		}
		if (type == 18) {
			return data != 0 ? "true" : "false";
		}
		if (type == 5) {
			return Float.toString(complexToFloat(data))
					+ DIMENSION_UNITS[(data & 0xF)];
		}
		if (type == 6) {
			return Float.toString(complexToFloat(data))
					+ FRACTION_UNITS[(data & 0xF)];
		}
		if ((type >= 28) && (type <= 31)) {
			return String.format("#%08X",
					new Object[] { Integer.valueOf(data) });
		}
		if ((type >= 16) && (type <= 31)) {
			return String.valueOf(data);
		}
		return String.format("<0x%X, type 0x%02X>",
				new Object[] { Integer.valueOf(data), Integer.valueOf(type) });
	}

	private String getPackage(int id) {
		if (id >>> 24 == 1) {
			return "android:";
		}
		return "";
	}

	public String AXMLToXML(byte[] axml) throws Exception {
		AXmlResourceParser parser = new AXmlResourceParser();
		ByteArrayInputStream bais = new ByteArrayInputStream(axml);
		parser.open(bais);
		StringBuilder indent = new StringBuilder(10);
		StringBuilder output = new StringBuilder(axml.length * 2);
		String indentStep = "   ";
		while (true) {
			int type = parser.next();
			if (type == 1) {
				break;
			}
			switch (type) {
			case 0:
				output.append(String.format(
						"<?xml version=\"1.0\" encoding=\"utf-8\"?>",
						new Object[0]));
				output.append("\n");
				break;
			case 2:
				output.append(String.format(
						"%s<%s%s",
						new Object[] { indent,
								getNamespacePrefix(parser.getPrefix()),
								parser.getName() }));
				output.append("\n");
				indent.append("   ");

				int namespaceCountBefore = parser.getNamespaceCount(parser
						.getDepth() - 1);
				int namespaceCount = parser
						.getNamespaceCount(parser.getDepth());
				for (int i = namespaceCountBefore; i != namespaceCount; i++) {
					output.append(String.format(
							"%sxmlns:%s=\"%s\"",
							new Object[] { indent,
									parser.getNamespacePrefix(i),
									parser.getNamespaceUri(i) }));
					output.append("\n");
				}

				for (int i = 0; i != parser.getAttributeCount(); i++) {
					output.append(String.format(
							"%s%s%s=\"%s\"",
							new Object[] {
									indent,
									getNamespacePrefix(parser
											.getAttributePrefix(i)),
									parser.getAttributeName(i),
									getAttributeValue(parser, i) }));
					output.append("\n");
				}
				output.append(String.format("%s>", new Object[] { indent }));
				output.append("\n");
				break;
			case 3:
				indent.setLength(indent.length() - "   ".length());
				output.append(String.format(
						"%s</%s%s>",
						new Object[] { indent,
								getNamespacePrefix(parser.getPrefix()),
								parser.getName() }));
				output.append("\n");
				break;
			case 4:
				output.append(String.format("%s%s", new Object[] { indent,
						parser.getText() }));
				output.append("\n");
			case 1:
			}
		}

		return output.toString();
	}

	public float complexToFloat(int complex) {
		return (complex & 0xFFFFFF00) * RADIX_MULTS[(complex >> 4 & 0x3)];
	}

	public void initDebugKeyStore() throws IOException {

		InputStream in = this.getClass().getResourceAsStream(
				"/res/debug.keystore");
		if (in == null) {
			System.out.println("in");
		}
		String userDir = System.getProperty("user.home");
		// InputStream is = new FileInputStream();
		OutputStream os = new FileOutputStream(new File(userDir
				+ "/.android/debug.keystore"));
		byte[] buf = new byte[1024];
		int len = 0;
		while ((len = in.read(buf)) != -1) {
			os.write(buf, 0, len);
		}
		in.close();
		os.close();

	}
}
