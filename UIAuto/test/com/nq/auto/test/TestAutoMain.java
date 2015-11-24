package com.nq.auto.test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.nq.auto.APKManger;
import com.nq.auto.AutoMain;
import com.nq.auto.Node;

import junit.framework.TestCase;

public class TestAutoMain extends TestCase {

	List<Element> allLeafs;

	protected void setUp() throws Exception {
		allLeafs = new ArrayList<Element>();
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testAutoMain() {
		String string = " mFocusedActivity: ActivityRecord{4190aa88 u0 com.netqin.aotkiller/.activity.MoreActivity}";
		int beginIndex = string.indexOf("{");
		string = string.substring(beginIndex, string.length());
		System.out.println(string);
		beginIndex = string.indexOf(" ");
		string = string.substring(beginIndex + 1, string.length());
		System.out.println(string);
		beginIndex = string.indexOf(" ");
		int endIndex = string.indexOf("/");
		string = string.substring(beginIndex + 1, endIndex);
		System.out.println(string);
	}

	public void testShowDevices() {
		fail("Not yet implemented");
	}

	public void testTimeStyle() {
		long t1 = Long.parseLong("1427254676617");
		long t2 = Long.parseLong("1427257674703");
		long between = t2 - t1;
		long hour = between / 1000 / 60 / 60;
		long min = (between / 1000 / 60) % 60;
		long second = (between / 1000) % 60;
		if (hour < 1) {
			System.out.println("Total time:" + min + " min " + second
					+ " second");
		} else {
			System.out.println("Total time:" + hour + " hour " + min + " min "
					+ second + " second");
		}

	}

	public void testInsertString() {

		StringBuffer str = new StringBuffer("/Users/admin/Downloads/tap.py 200 400");
		System.out.println(str);
		int index = str.indexOf(" ");
		str=str.insert(index, " -d desdfdsfdsfsf");
		System.out.println(str.toString());
	}

	public void testGetUiHierarchyFile() {
		fail("Not yet implemented");
	}

	public void testMain() {
		fail("Not yet implemented");
	}

	public void testGetAPKManger() {
		APKManger apkManger = new APKManger();
		try {
			File f = File.createTempFile("resigner", ".apk");
			String[] result = apkManger
					.stripSigning(
							"/Users/admin/Downloads/MobileBooster30-Android-trunk_r2076-release-b271.apk",
							f.getAbsolutePath());
			System.out.println(result[0]);
			System.out.println(result[1]);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void testGetAllNodes() {
		ArrayList arraylist = new ArrayList();
		AutoMain autoMain = new AutoMain("");
		File inputXml = new File("src/dump_3286992284390031491.xml");
		File inputXml1 = new File("src/dump_7707164985894485124.xml");
		List<Element> list1 = autoMain.getAllLeafNode(inputXml);
		List<Element> list2 = autoMain.getAllLeafNode(inputXml1);
		ArrayList<Node> nodeList1 = autoMain.getNodesList(list1, null);
		ArrayList<Node> nodeList2 = autoMain.getNodesList(list2, null);

		if (nodeList1.size() > nodeList2.size()) {
			System.out.println("nodeList1节点多");
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < nodeList1.size(); i++) {
				sb.append(nodeList1.get(i).getX());
				sb.append(nodeList1.get(i).getY());
			}
			System.out.println(sb.toString());

			StringBuffer sb2 = new StringBuffer();
			for (int i = 0; i < nodeList2.size(); i++) {
				sb2.append(nodeList2.get(i).getX());
				sb2.append(nodeList2.get(i).getY());
			}
			System.out.println(sb2.toString());

			float diffient = getSimilarityRatio(sb2.toString(), sb.toString());
			System.out.println("不同点有：" + diffient);
			// for (int i = 0; i < nodeList2.size(); i++) {
			// for (int j = 0; j < nodeList1.size(); j++) {
			// if (nodeList2.get(i).getX() == nodeList1.get(j).getX()
			// && nodeList2.get(i).getY() == nodeList1.get(j)
			// .getY()) {
			//
			// }
			// }
			// }
		} else if (nodeList1.size() < nodeList2.size()) {
			System.out.println("nodeList2节点多");
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < nodeList1.size(); i++) {
				sb.append(nodeList1.get(i).getX());
				sb.append(nodeList1.get(i).getY());
			}
			System.out.println(sb.toString());

			StringBuffer sb2 = new StringBuffer();
			for (int i = 0; i < nodeList2.size(); i++) {
				sb2.append(nodeList2.get(i).getX());
				sb2.append(nodeList2.get(i).getY());
			}
			System.out.println(sb2.toString());

			float diffient = getSimilarityRatio(sb2.toString(), sb.toString());
			System.out.println("不同点有：" + diffient);

		} else {
			System.out.println("nodeList1和nodeList2节点一样多");
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < nodeList1.size(); i++) {
				sb.append(nodeList1.get(i).getX());
				sb.append(nodeList1.get(i).getY());
			}
			System.out.println(sb.toString());

			StringBuffer sb2 = new StringBuffer();
			for (int i = 0; i < nodeList2.size(); i++) {
				sb2.append(nodeList2.get(i).getX());
				sb2.append(nodeList2.get(i).getY());
			}
			System.out.println(sb2.toString());

			float diffient = getSimilarityRatio(sb2.toString(), sb.toString());
			System.out.println("不同点有：" + diffient);
		}

		// SAXReader saxReader = new SAXReader();
		// try {
		// Document doc = saxReader.read(inputXml);
		// Element rootElement = doc.getRootElement();
		// Element testNode = rootElement.element("test");
		// Element classesNodes = testNode.element("classes");
		// for (Iterator i = classesNodes.elementIterator("class"); i
		// .hasNext();) {
		// Element classNode = (Element) i.next();
		//
		// Map<String, Object> moudleMap = new HashMap<String, Object>();
		//
		// System.out.println("模块：" + classNode.attributeValue("name"));
		//
		// Element methodsNodes = classNode.element("methods");
		//
		// ArrayList testCaseList = new ArrayList();
		// for (Iterator j = methodsNodes.elementIterator("include"); j
		// .hasNext();) {
		// Element node = (Element) j.next();
		// System.out.println(node.attributeValue("name").toString());
		//
		// }
		//
		// System.out.println(testCaseList.size());
		//
		// if (testCaseList.size() != 0) {
		// moudleMap
		// .put("className", classNode.attributeValue("name"));
		// moudleMap.put("testCaseList", testCaseList);
		// arraylist.add(moudleMap);
		// }
		//
		// }
		//
		// System.out.println("当前共有：" + arraylist.size());
		//
		// } catch (DocumentException e) {
		//
		// System.out.println(e.getMessage());
		//
		// }
	}

	public void testGetAllXmlNodes() {
		ArrayList arraylist = new ArrayList();
		AutoMain autoMain = new AutoMain("");
		File inputXml = new File("src/dump_3037321996356545826.xml");
		List<Element> list1 = autoMain.getAllNode(inputXml);
		for (int i = 0; i < list1.size(); i++) {
			if (list1.get(i).attributeValue("scrollable").contains("true")) {
				System.out.println(list1.get(i).attributeValue("resource-id"));
			}
		}
		// SAXReader saxReader = new SAXReader();
		// try {
		// Document doc = saxReader.read(inputXml);
		// Element rootElement = doc.getRootElement();
		// Element testNode = rootElement.element("test");
		// Element classesNodes = testNode.element("classes");
		// for (Iterator i = classesNodes.elementIterator("class"); i
		// .hasNext();) {
		// Element classNode = (Element) i.next();
		//
		// Map<String, Object> moudleMap = new HashMap<String, Object>();
		//
		// System.out.println("模块：" + classNode.attributeValue("name"));
		//
		// Element methodsNodes = classNode.element("methods");
		//
		// ArrayList testCaseList = new ArrayList();
		// for (Iterator j = methodsNodes.elementIterator("include"); j
		// .hasNext();) {
		// Element node = (Element) j.next();
		// System.out.println(node.attributeValue("name").toString());
		//
		// }
		//
		// System.out.println(testCaseList.size());
		//
		// if (testCaseList.size() != 0) {
		// moudleMap
		// .put("className", classNode.attributeValue("name"));
		// moudleMap.put("testCaseList", testCaseList);
		// arraylist.add(moudleMap);
		// }
		//
		// }
		//
		// System.out.println("当前共有：" + arraylist.size());
		//
		// } catch (DocumentException e) {
		//
		// System.out.println(e.getMessage());
		//
		// }
	}

	public int hammingDistance(String sourceHashCode, String hashCode) {
		int difference = 0;
		int len = sourceHashCode.length();

		for (int i = 0; i < len; i++) {
			if (sourceHashCode.charAt(i) != hashCode.charAt(i)) {
				difference++;
			}
		}

		return difference;
	}

	public void testGetCurrentActivityName() {
		String string = "mFocusedActivity: ActivityRecord{416a1c78 com.android.launcher/com.android.launcher2.Launcher}";
		int beginIndex = string.indexOf("/");
		int endIndex = string.indexOf("}");
		System.out.println(string.substring(beginIndex + 1, endIndex));
	}

	public void testGetCurrentPackAgeName() {
		String line = "mFocusedActivity: ActivityRecord{416a1c78 com.android.launcher/com.android.launcher2.Launcher}";
		int beginIndex = line.indexOf("{");
		line = line.substring(beginIndex, line.length());
		beginIndex = line.indexOf(" ");
		line = line.substring(beginIndex + 1, line.length());
		beginIndex = line.indexOf(" ");
		int endIndex = line.indexOf("/");
		line = line.substring(beginIndex + 1, endIndex);
		System.out.println(line);
	}

	private int compare(String str, String target) {
		int d[][]; // 矩阵
		int n = str.length();
		int m = target.length();
		int i; // 遍历str的
		int j; // 遍历target的
		char ch1; // str的
		char ch2; // target的
		int temp; // 记录相同字符,在某个矩阵位置值的增量,不是0就是1
		if (n == 0) {
			return m;
		}
		if (m == 0) {
			return n;
		}
		d = new int[n + 1][m + 1];
		for (i = 0; i <= n; i++) { // 初始化第一列
			d[i][0] = i;
		}

		for (j = 0; j <= m; j++) { // 初始化第一行
			d[0][j] = j;
		}

		for (i = 1; i <= n; i++) { // 遍历str
			ch1 = str.charAt(i - 1);
			// 去匹配target
			for (j = 1; j <= m; j++) {
				ch2 = target.charAt(j - 1);
				if (ch1 == ch2) {
					temp = 0;
				} else {
					temp = 1;
				}

				// 左边+1,上边+1, 左上角+temp取最小
				d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1]
						+ temp);
			}
		}
		return d[n][m];
	}

	private int min(int one, int two, int three) {
		return (one = one < two ? one : two) < three ? one : three;
	}

	/**
	 * 
	 * 获取两字符串的相似度
	 * 
	 * 
	 * 
	 * @param str
	 * 
	 * @param target
	 * 
	 * @return
	 */

	public float getSimilarityRatio(String str, String target) {
		return 1 - (float) compare(str, target)
				/ Math.max(str.length(), target.length());
	}

	public void testGetFilePath() {
		String filePath = "/Users/admin/Downloads/SuperTaskKiller2.0-Android-trunk_r662-release-b70.apk";
		File file = new File(filePath);
		System.out.println(file.getParent());
	}
	
	public void test1() {
		AutoMain autoMain=new AutoMain("");
		System.out.println(autoMain.getDevicesCount());
	}


}
