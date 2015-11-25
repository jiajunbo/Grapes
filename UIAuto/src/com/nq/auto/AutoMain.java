package com.nq.auto;

import java.awt.Dialog;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.CollectingOutputReceiver;

/**
 * 自动化遍历的主类和启动类
 * 
 * @author jiakui
 * 
 */
public class AutoMain {

	/**
	 * 由命令行传入的设备串号
	 */
	private static String serial;

	/**
	 * 由命令行传入待测应用apk安装包的绝对路径
	 */
	private static String apkPath;

	/**
	 * 当前设备串号
	 */
	private String deviceSerial;

	/**
	 * 待测apk的父路径
	 */
	private String apkParentPath;

	/**
	 * 测试深度的定义，限制树的测试深度
	 */
	private int testDepth = 4;

	/**
	 * 与ADB操作相关的管理类
	 */
	public ADBManger adbManger;

	/**
	 * 所有由xml文件获取的子结点
	 */
	public List<Element> allLeafs;

	/**
	 * 所有由xml文件获取的结点
	 */
	public List<Element> allXmlLeafs;

	/**
	 * 当前系统中所有的设备的列表
	 */
	public IDevice[] deviceList;

	/**
	 * 当前设备的IDevice对象
	 */
	public IDevice device;

	/**
	 * 当前apk的启动类
	 */
	private String launcherActivityName;

	/**
	 * 当前apk在启动界面后现实主界面的类名，不一定是启动类的名称
	 */
	private String mainActivityName;

	/**
	 * 当前手机在运行在最前端的应用的包名
	 */
	private String currentPackageName;

	/**
	 * 待测应用的包名
	 */
	private String packageName;

	/**
	 * apk分析的管理类
	 */
	public APKManger apkManger;

	/**
	 * apk当前的包名和启动类存储的字符串数组，0为包名，1为启动类的名称
	 */
	private String apkInfo[];

	/**
	 * 记录已测试过的activity名称
	 */
	private ArrayList<String> activityNameList = new ArrayList<String>();

	/**
	 * 用例失败后的所有错误详情
	 */
	private String logcatLine;

	/**
	 * 监控日志的标记
	 */
	private boolean isRunning = true;

	/**
	 * logcat的主进程
	 */
	private Process logcatProcess;

	/**
	 * 默认的无参数构造方法
	 */
	public AutoMain() {

		// 实例化相应的管理类
		adbManger = new ADBManger();
		apkManger = new APKManger();

		// 定义一个临时文件用于分析当前apk的包名和启动类
		File file;
		apkParentPath = new File(apkPath).getParent();
		if (serial == null) {
			serial = "";
		}
		if (createDir(apkParentPath + "/screenshot" + serial)) {
			System.out.println("create screenshot dir success!");
		}

		try {
			file = File.createTempFile("temp", ".apk");
			try {
				apkInfo = apkManger.stripSigning(apkPath,
						file.getAbsolutePath());
				packageName = apkInfo[0];
				launcherActivityName = apkInfo[1];
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (serial != null && serial != "") {
			deviceSerial = " -s " + serial;
			// 以下是获取设备列表，所有子节点
			deviceList = adbManger.bridge.getDevices();
			for (int i = 0; i < deviceList.length; i++) {
				if (deviceList[i].getSerialNumber().contains(serial)) {
					device = deviceList[i];
					break;
				}
			}
		} else {
			deviceSerial = "";
			// 以下是获取设备列表，所有子节点
			deviceList = adbManger.bridge.getDevices();
			if (deviceList.length > 1) {
				printAndExit(
						"Error: more than one emulator or device available!",
						true);
			} else {
				device = deviceList[0];
			}
		}

		allLeafs = new ArrayList<Element>();

		// 首先将应用强制关闭在启动，防止启动时应用处于当开状态无法进入到主屏幕
		finishOpenedActivities();
		startLauncherActivity();
		mainActivityName = this.getCurrentActivityName();

		// 启动测试
		startTest();

	}

	/**
	 * 生成目录
	 * 
	 * @param destDirName
	 *            目录名称
	 * @return 返回目录是否生成或目录已存在
	 */
	public boolean createDir(String destDirName) {
		File dir = new File(destDirName);
		if (dir.exists()) {
			return true;

		} else {
			if (!destDirName.endsWith(File.separator)) {
				destDirName = destDirName + File.separator;
			}
			if (dir.mkdirs()) {
				return true;
			} else {
				return false;
			}
		}

	}

	public void startTest() {

		monitorLogCat();
		// 获取新的uidump.xml文件并生成
		this.getUiHierarchyFile(
				device,
				new File(System.getProperty("user.home")
						+ System.getProperty("file.separator") + "uidump"
						+ serial + ".xml"));
		File inputXml = new File(System.getProperty("user.home")
				+ System.getProperty("file.separator") + "uidump" + serial
				+ ".xml");
		List<Element> all = this.getAllLeafNode(inputXml);

		// 现实当前主activity所有可点击的控件数目
		System.out.println("Views:" + all.size());

		// 定义根节点中每个控件的路径列表，根节点路径列表为空，用作初始化
		ArrayList<Node> pathList = new ArrayList<Node>();

		// 获取当前activity中所有控件的Node对象的列表
		ArrayList<Node> nodeList = getNodesList(
				this.getAllElement(all, inputXml), pathList);

		// 打印出主界面所有的控件的node的x,y的位置信息
		for (int i = 0; i < nodeList.size(); i++) {
			System.out.println(nodeList.get(i).getX() + ","
					+ nodeList.get(i).getY());
		}
		// 定义根节点进行初始化
		Node node = new Node();
		node.setActivityName(getCurrentActivityName());

		// 启动遍历算法传入节点列表，遍历层次序号以第0层定义为主界面，和父节点
		long time1 = System.currentTimeMillis();
		forEachViews(nodeList, 0, node);
		long time2 = System.currentTimeMillis();
		long between = time2 - time1;

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

		// 对截出来来得图片进行去重，除去相似度大于为5的图片保留有UI有明显变化的图片
		ArrayList fileList;
		try {
			fileList = this.getRepeatImageList();
			int count = fileList.size();
			for (int i = 0; i < count; i++) {
				File temp = new File(fileList.get(i).toString());
				temp.delete();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		isRunning = false;

	}

	public AutoMain(String string) {
	}

	/**
	 * 判断当前activity是否已经遍历过了
	 */
	public boolean isTraversal(String activityName) {
		int count = activityNameList.size();
		boolean bool = false;
		for (int i = 0; i < count; i++) {
			if (activityName.equals(activityNameList.get(i))) {
				bool = true;
			}
		}
		return bool;
	}

	/**
	 * 从monkey截图中分析出相同的图片进行去重
	 * 
	 * @return
	 * @throws Exception
	 */
	public ArrayList getRepeatImageList() throws Exception {
		ImageUtil imageUtil = new ImageUtil();
		File file = new File(apkPath);
		ArrayList arrayList = this.getFingerPrintList(apkParentPath
				+ "/screenshot" + serial);

		ArrayList delList = new ArrayList();
		for (int i = 0; i < arrayList.size() - 1; i++) {
			ArrayList sameList = new ArrayList();
			HashMap sameHashMap = new HashMap();
			HashMap<String, String> hashMap = (HashMap) arrayList.get(i);
			sameHashMap.put("fileName", hashMap.get("fileName"));
			for (int j = 0; j < arrayList.size() - 1; j++) {
				HashMap<String, String> hashMap1 = (HashMap) arrayList.get(j);
				if (i != j) {
					int difference = imageUtil.hammingDistance(
							hashMap.get("fingerPrint"),
							hashMap1.get("fingerPrint"));
					if (difference == 0) {
						sameList.add(hashMap1);
					} else if (difference <= 5) {
						// delList.add(hashMap1);
						// System.out.println(hashMap.get("fileName") + ","
						// + hashMap1.get("fileName") + "非常相似");
						// System.out.println("source.jpg图片跟example" +
						// ".jpg非常相似");
					} else if (difference <= 10) {
						// System.out.println("source.jpg图片跟example" +
						// ".jpg有点相似");
					} else if (difference > 10) {
						// System.out.println("source.jpg图片跟example" +
						// ".jpg完全不一样");
					}
				}

			}
			sameHashMap.put("sameList", sameList);
			delList.add(sameHashMap);
		}

		ArrayList list = new ArrayList();
		for (int i = 0; i < delList.size(); i++) {
			HashMap hashMap = (HashMap) delList.get(i);
			String fileName = hashMap.get("fileName").toString();
			ArrayList sameList = (ArrayList) hashMap.get("sameList");
			if (sameList.size() != 0) {
				for (int j = 0; j < sameList.size(); j++) {
					if (isExist(list, fileName)) {
						HashMap hm = (HashMap) sameList.get(j);
						list.add(hm.get("fileName"));
					}
				}
			}

		}
		System.out.println("have image is repeat:" + list.size()
				+ ",haven remove!");

		return list;

	}

	/**
	 * 监控LogCat生成测试报告
	 */
	public void monitorLogCat() {
		new Thread(new Runnable() {
			public void run() {
				try {
					Runtime.getRuntime().exec(
							"adb" + deviceSerial + " logcat -c");
					Thread.sleep(2000);
					logcatProcess = Runtime.getRuntime().exec(
							"adb" + deviceSerial + " logcat");
					BufferedReader logcatBufferedReader = new BufferedReader(
							new InputStreamReader(logcatProcess
									.getInputStream(), "utf-8"));
					isRunning = true;
					while (isRunning) {
						if ((logcatLine = logcatBufferedReader.readLine()) != null) {
							if (logcatLine.contains("E/ActivityManager")
									&& logcatLine.contains("ANR")) {

								processAnrThread();

							} else if (logcatLine.contains("E/AndroidRuntime")
									&& logcatLine.contains("FATAL")) {

								processFatalThread();

							} else if (logcatLine.contains("E/AndroidRuntime")
									&& logcatLine
											.contains("Activity Manager Crash")) {

								processFatalThread();

							}

						}
					}

					System.exit(0);

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * 当出现强制关闭时处理的线程
	 */
	public void processFatalThread() {
		new Thread(new Runnable() {
			public void run() {
				try {
					clickCrashButton();
					// clickCrashButton();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * 当出现ANR类型的崩溃时进行处理的线程
	 */
	public void processAnrThread() {
		new Thread(new Runnable() {
			public void run() {
				try {
					clickCrashButton();

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * 通过adb命令将界面上出现的崩溃按钮点击消失
	 * 
	 * @return
	 */
	public String clickCrashButton() {
		String result = "";
		try {
			Thread.sleep(2000);
			Process process1 = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell input keyevent 20");
			BufferedReader br1 = new BufferedReader(new InputStreamReader(
					process1.getInputStream()));
			boolean bool1 = br1.ready();
			Thread.sleep(2000);

			Process process2 = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell input keyevent 23");
			BufferedReader br2 = new BufferedReader(new InputStreamReader(
					process2.getInputStream()));
			boolean bool2 = br2.ready();
			Thread.sleep(2000);

			if (!bool1 && !bool2) {
				result = "success";
			} else {
				result = "failure";
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;

	}

	/**
	 * 获取所有要发布的图片的指纹信息
	 * 
	 * @param projectPath
	 * @return
	 */
	public ArrayList<HashMap<String, Object>> getFingerPrintList(
			String projectPath) {

		SearchFile searchFile = new SearchFile();
		ArrayList fingerPrintList = new ArrayList();
		try {
			ArrayList fileList = searchFile.findAllFiles(projectPath);

			ArrayList imageFileList = new ArrayList();
			for (int i = 0; i < fileList.size(); i++) {
				if (fileList.get(i).toString().contains(".jpg")) {
					imageFileList.add(fileList.get(i));
				}
			}

			int count = imageFileList.size();
			ImageUtil imageUtil = new ImageUtil();
			HashMap<String, String> hashMap;
			for (int i = 0; i < count; i++) {
				hashMap = new HashMap<String, String>();
				hashMap.put("fileName", imageFileList.get(i).toString());
				hashMap.put("fingerPrint", imageUtil
						.produceFingerPrint(imageFileList.get(i).toString()));
				fingerPrintList.add(hashMap);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return fingerPrintList;

	}

	/**
	 * 在list中检索string是否存在
	 * 
	 * @param list
	 * @param string
	 * @return
	 */
	public boolean isExist(ArrayList list, String string) {
		for (int i = 0; i < list.size(); i++) {
			if (string.contains(list.get(i).toString())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 现实当前程序获取到的设备信息，供单元测试使用
	 */
	public void showDevices() {

		for (int i = 0; i < deviceList.length; i++) {
			System.out.println(deviceList[i].getSerialNumber());
		}

		this.getUiHierarchyFile(device, new File("/tmp/uidump" + serial
				+ ".xml"));
	}

	/**
	 * 启动当前待测应用
	 */
	public void startLauncherActivity() {
		try {

			Process process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell am start -n " + apkInfo[0]
							+ "/" + apkInfo[1]);
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				System.out.println(line);
			}
			process.destroy();
			Thread.sleep(8000);

		} catch (Exception e) {
			// TODO: handle exception
		}

	}

	/**
	 * 强制关闭当前应用，防止启动时无法正常启动
	 */
	public void finishOpenedActivities() {
		try {

			Process process = Runtime.getRuntime()
					.exec("adb" + deviceSerial + " shell am force-stop "
							+ apkInfo[0]);
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				System.out.println(line);
			}
			process.destroy();
			Thread.sleep(3000);
		} catch (Exception e) {
			// TODO: handle exception
		}

	}

	/**
	 * 强制关闭指定包名
	 * 
	 * @param string
	 *            当前应用的包名
	 */
	public void finishOpenedActivities(String string) {
		try {

			Process process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell am force-stop " + string);
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				System.out.println(line);
			}
			process.destroy();
			Thread.sleep(3000);

		} catch (Exception e) {
			// TODO: handle exception
		}

	}

	/**
	 * 判断当前应用是否支持uiautomator
	 * 
	 * @param device
	 *            当前设备的IDevice对象
	 * @return
	 */
	public boolean supportsUiAutomator(IDevice device) {
		String apiLevelString = device.getProperty("ro.build.version.sdk");
		int apiLevel;
		try {
			apiLevel = Integer.parseInt(apiLevelString);
		} catch (NumberFormatException e) {
			apiLevel = 16;
		}

		return apiLevel >= 16;
	}

	/**
	 * 获得当前系统当前可用连接的android设备
	 */
	public int getDevicesCount() {

		int count = 0;
		try {

			Process devicesProcess = Runtime.getRuntime().exec("adb devices");
			BufferedReader devicesBufferedReader = new BufferedReader(
					new InputStreamReader(devicesProcess.getInputStream()));
			String devicesline;
			boolean isrunning = true;

			while (isrunning) {
				if ((devicesline = devicesBufferedReader.readLine()) != null) {
					if (!devicesline
							.contains("daemon not running. starting it now on port 5037")
							&& !devicesline
									.contains("daemon started successfully")
							&& !devicesline
									.contains("List of devices attached")) {
						count += 1;

					}

					// if (logcatline.contains("E/ActivityManager")
					// && logcatline.contains("ANR")) {
					// failure = getStackTraceLine(logcatline) + "\n";
					// failMessage = failure;
					// }
				} else {
					isrunning = false;
					devicesProcess.destroy();
				}
			}

		} catch (Exception e) {
			// TODO: handle exception
		}
		return count;
	}

	/**
	 * 获取当前界面的xml文件
	 * 
	 * @param device
	 *            当前设备的IDevice对象
	 * @param dst
	 *            需要获取到文件推送到的本地路径
	 */
	@SuppressWarnings("deprecation")
	public void getUiHierarchyFile(IDevice device, File dst) {

		if (adbManger.bridge.getBridge().hasInitialDeviceList()) {
			deviceList = adbManger.bridge.getDevices();
		}
		File xmlDumpFile = dst;
		String command = "rm /data/local/tmp/uidump" + serial + ".xml";
		try {
			CountDownLatch commandCompleteLatch = new CountDownLatch(1);
			device.executeShellCommand(command, new CollectingOutputReceiver(
					commandCompleteLatch));

			commandCompleteLatch.await(50L, TimeUnit.SECONDS);
		} catch (Exception e1) {
		}
		command = String.format("%s %s %s", new Object[] {
				"/system/bin/uiautomator", "dump",
				"/data/local/tmp/uidump" + serial + ".xml" });
		CountDownLatch commandCompleteLatch = new CountDownLatch(1);
		try {
			device.executeShellCommand(command, new CollectingOutputReceiver(
					commandCompleteLatch), 50000);
			commandCompleteLatch.await(50L, TimeUnit.SECONDS);
			device.getSyncService().pullFile(
					"/data/local/tmp/uidump" + serial + ".xml",
					xmlDumpFile.getAbsolutePath(),
					SyncService.getNullProgressMonitor());
		} catch (Exception e) {
			// throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			if (args.length == 0) {
				printUsageAndQuit();
			} else {

				// parse command line parameters.
				int index = 0;
				do {
					String argument = args[index++];
					if ("-s".equals(argument)) {
						// quick check on the next argument.
						if (index == args.length) {
							printAndExit("Missing serial number after -s",
									false /* terminate */);
						}
						serial = args[index++];
					} else if ("-a".equals(argument)) {
						if (index == args.length) {
							printAndExit("Missing apk path after -a", false /* terminate */);
						}
						apkPath = args[index++];

					}
				} while (index < args.length);

			}

			if (apkPath != null) {
				AutoMain autoMain = new AutoMain();
				System.out.println("running finsh!");

			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 从xml文件获取当前xml的所有子节点
	 * 
	 * @param file
	 * @return
	 */
	public List<Element> getAllLeafNode(File file) {
		SAXReader reader = new SAXReader();
		allLeafs = null;
		allLeafs = new ArrayList<Element>();
		Document doc;
		try {
			doc = reader.read(file);
			Element root = doc.getRootElement();
			Iterator<Element> allSons = root.elementIterator();
			while (allSons.hasNext()) {
				getLeafNodes(allSons.next());
			}
		} catch (DocumentException e) {
			e.printStackTrace();
		}
		return allLeafs;
	}

	/**
	 * 从xml文件获取当前xml的所有子节点
	 * 
	 * @param file
	 * @return
	 */
	public List<Element> getAllNode(File file) {
		SAXReader reader = new SAXReader();
		allXmlLeafs = null;
		allXmlLeafs = new ArrayList<Element>();
		Document doc;
		try {
			doc = reader.read(file);
			Element root = doc.getRootElement();
			Iterator<Element> allSons = root.elementIterator();
			while (allSons.hasNext()) {
				getXmlNodes(allSons.next());
			}
		} catch (DocumentException e) {
			e.printStackTrace();
		}
		return allXmlLeafs;
	}

	/**
	 * 获取当前节点的所有子节点
	 * 
	 * @param currentNode
	 * @return
	 */
	public List<Element> getLeafNodes(Element currentNode) {
		Element e = currentNode;
		if ((e.elements()).size() >= 0) {
			List<Element> el = e.elements();
			for (Element sonNode : el) {
				if (sonNode.elements().size() > 0)
					getLeafNodes(sonNode);
				else
					allLeafs.add(sonNode);
			}
		}
		return allLeafs;
	}

	/**
	 * 获取当前节点的所有子节点
	 * 
	 * @param currentNode
	 * @return
	 */
	public List<Element> getXmlNodes(Element currentNode) {
		Element e = currentNode;
		if ((e.elements()).size() >= 0) {
			List<Element> el = e.elements();
			for (Element sonNode : el) {
				if (sonNode.elements().size() > 0) {
					getXmlNodes(sonNode);
					allXmlLeafs.add(sonNode);
				} else
					allXmlLeafs.add(sonNode);
			}
		}
		return allXmlLeafs;
	}

	/**
	 * 将xml中的位置信息转换为node类型的对象
	 * 
	 * @param location
	 *            在xml当中的位置信息
	 * @param activityName
	 *            当前节点所处的activity名称
	 * @param nodeList
	 *            当前节点的路径列表
	 * @return
	 */
	public Node getNodeLocation(String location, String activityName,
			ArrayList<Node> nodeList, String scrollable, String nodeType) {
		int beginIndex = 0;
		int middleIndex = 0;
		int endIndex = 0;
		beginIndex = location.indexOf("[");
		middleIndex = location.indexOf(",");
		endIndex = location.indexOf("]");
		int x = Integer.parseInt(location
				.substring(beginIndex + 1, middleIndex));
		int y = Integer.parseInt(location.substring(middleIndex + 1, endIndex));
		String bounds = location.substring(endIndex + 1, location.length());

		beginIndex = 0;
		middleIndex = 0;
		endIndex = 0;
		beginIndex = bounds.indexOf("[");
		middleIndex = bounds.indexOf(",");
		endIndex = bounds.indexOf("]");
		int width = Integer.parseInt(bounds.substring(beginIndex + 1,
				middleIndex));
		int height = Integer.parseInt(bounds.substring(middleIndex + 1,
				endIndex));

		Node node = new Node();
		node.setX(x + (width - x) / 2);
		node.setY(y + (height - y) / 2);
		node.setActivityName(activityName);
		node.setLocationX(x);
		node.setLocationY(y);
		node.setHeight(height);
		node.setWidth(width);
		ArrayList<Node> nodes = new ArrayList<Node>();
		for (int i = 0; i < nodeList.size(); i++) {
			nodes.add(nodeList.get(i));
		}
		nodes.add(node);
		node.setNodesList(nodes);
		if (scrollable.contains("true")) {
			node.setScrollable(true);
		}
		node.setNodeType(nodeType);
		return node;

	}

	/**
	 * 将xml中的位置信息转换为node类型的对象
	 * 
	 * @param location
	 *            在xml当中的位置信息
	 * @param activityName
	 *            当前节点所处的activity名称
	 * @param nodeList
	 *            当前节点的路径列表
	 * @return
	 */
	public Node getNodeLocation(String location, String activityName,
			ArrayList<Node> nodeList, Boolean bool, String scrollable,
			String nodeType) {
		int beginIndex = 0;
		int middleIndex = 0;
		int endIndex = 0;
		beginIndex = location.indexOf("[");
		middleIndex = location.indexOf(",");
		endIndex = location.indexOf("]");
		int x = Integer.parseInt(location
				.substring(beginIndex + 1, middleIndex));
		int y = Integer.parseInt(location.substring(middleIndex + 1, endIndex));
		String bounds = location.substring(endIndex + 1, location.length());

		beginIndex = 0;
		middleIndex = 0;
		endIndex = 0;
		beginIndex = bounds.indexOf("[");
		middleIndex = bounds.indexOf(",");
		endIndex = bounds.indexOf("]");
		int width = Integer.parseInt(bounds.substring(beginIndex + 1,
				middleIndex));
		int height = Integer.parseInt(bounds.substring(middleIndex + 1,
				endIndex));

		Node node = new Node();
		node.setX(x + (width - x) / 2);
		node.setY(y + (height - y) / 2);
		node.setActivityName(activityName);
		node.setFromMenu(bool);
		node.setLocationX(x);
		node.setLocationY(y);
		node.setHeight(height);
		node.setWidth(width);
		ArrayList<Node> nodes = new ArrayList<Node>();
		for (int i = 0; i < nodeList.size(); i++) {
			nodes.add(nodeList.get(i));
		}
		nodes.add(node);
		node.setNodesList(nodes);
		if (scrollable.contains("true")) {
			node.setScrollable(true);
		}
		node.setNodeType(nodeType);
		return node;

	}

	/**
	 * 将xml中的位置信息转换为node类型的对象,但不加路径列表
	 * 
	 * @param location
	 * @param activityName
	 * @return
	 */
	public Node getNodeLocation(String location, String activityName) {
		int beginIndex = 0;
		int middleIndex = 0;
		int endIndex = 0;
		beginIndex = location.indexOf("[");
		middleIndex = location.indexOf(",");
		endIndex = location.indexOf("]");
		int x = Integer.parseInt(location
				.substring(beginIndex + 1, middleIndex));
		int y = Integer.parseInt(location.substring(middleIndex + 1, endIndex));
		String bounds = location.substring(endIndex + 1, location.length());

		beginIndex = 0;
		middleIndex = 0;
		endIndex = 0;
		beginIndex = bounds.indexOf("[");
		middleIndex = bounds.indexOf(",");
		endIndex = bounds.indexOf("]");
		int width = Integer.parseInt(bounds.substring(beginIndex + 1,
				middleIndex));
		int height = Integer.parseInt(bounds.substring(middleIndex + 1,
				endIndex));

		Node node = new Node();
		node.setX(x + (width - x) / 2);
		node.setY(y + (height - y) / 2);
		node.setLocationX(x);
		node.setLocationY(y);
		node.setHeight(height);
		node.setWidth(width);
		node.setActivityName(activityName);
		return node;

	}

	/**
	 * 获取当前界面所有的节点的node对象列表
	 * 
	 * @param list
	 *            xml格式的所有子节点的列表
	 * @param nodeList
	 *            当前界面的路径列表
	 * @return
	 */
	public ArrayList<Node> getNodesList(List<Element> list,
			ArrayList<Node> nodeList) {
		ArrayList<Node> arrayList = new ArrayList<Node>();
		int count = list.size();
		for (int i = 0; i < count; i++) {
			Element element = list.get(i);
			if (!element.attributeValue("class").contains("Layout")) {
				String scrollable = element.attributeValue("scrollable");
				String nodeType = element.attributeValue("class");
				arrayList.add(this.getNodeLocation(
						element.attributeValue("bounds"),
						this.getCurrentActivityName(), nodeList, scrollable,
						nodeType));
			}

		}
		return arrayList;
	}

	/**
	 * 获取当前界面所有的节点的node对象列表
	 * 
	 * @param list
	 *            xml格式的所有子节点的列表
	 * @param nodeList
	 *            当前界面的路径列表
	 * @return
	 */
	public ArrayList<Node> getNodesList(List<Element> list,
			ArrayList<Node> nodeList, Boolean bool) {
		ArrayList<Node> arrayList = new ArrayList<Node>();
		int count = list.size();
		for (int i = 0; i < count; i++) {
			Element element = list.get(i);
			if (!element.attributeValue("class").contains("Layout")) {
				String scrollable = element.attributeValue("scrollable");
				String nodeType = element.attributeValue("class");
				arrayList.add(this.getNodeLocation(
						element.attributeValue("bounds"),
						this.getCurrentActivityName(), nodeList, bool,
						scrollable, nodeType));
			}

		}
		return arrayList;
	}

	/**
	 * 获取当前界面所有的节点的node对象列表
	 * 
	 * @param xml格式的所有子节点的列表
	 * @return
	 */
	public ArrayList<Node> getNodesList(List<Element> list) {
		ArrayList<Node> arrayList = new ArrayList<Node>();
		int count = list.size();
		for (int i = 0; i < count; i++) {
			Element element = list.get(i);

			if (!element.attributeValue("class").contains("Layout")) {
				arrayList.add(this.getNodeLocation(
						element.attributeValue("bounds"),
						this.getCurrentActivityName()));
			}

		}
		return arrayList;
	}

	public boolean isDeviceState(String device) {
		ArrayList arrayList = this.getDevices();
		boolean flag = false;
		for (int i = 0; i < arrayList.size(); i++) {
			if (arrayList.get(i).toString().contains(device)) {
				flag = true;
			}
		}
		return flag;
	}

	/**
	 * 遍历当前activity当中界面的所有控件进行点击，如果有新界面产生进行递归调用
	 * 
	 * @param nodeList
	 *            当前界面的所有可点击的节点列表
	 * @param top
	 *            当前界面所处于那一层的层号
	 * @param fatherNode
	 *            当前界面的父节点
	 * @return
	 */
	public boolean forEachViews(ArrayList<Node> nodeList, int top,
			Node fatherNode) {

		// 首先对传入的节点列表进行遍历
		for (int i = 0; i < nodeList.size(); i++) {

			if (serial != null) {
				if (!isDeviceState(serial)) {
					restartADB();
				}
			} else {
				if (getDevicesCount() == 0) {
					restartADB();
				}
			}

			// 如果当前是在第0层每次都先关闭被侧应用在从新启动
			if (top == 0) {
				finishOpenedActivities();
				startLauncherActivity();
				// if (!this.getCurrentActivityName().equals(mainActivityName))
				// {
				// finishOpenedActivities();
				// startLauncherActivity();
				// }

			}

			// 此部分为了判断当前节点所处得activity是否和当前应用所在得activity一致,如果不一致从节点路径点击回到当前节点所处的activity
			if (!nodeList.get(i).getActivityName()
					.equals(this.getCurrentActivityName())
					|| nodeList.get(i).isFromMenu()) {
				if (top != 0) {
					if (!this.getCurrentActivityName().equals(mainActivityName)) {
						finishOpenedActivities();
						startLauncherActivity();
					}
				}

				// int nodesListCount = nodeList.get(i).getNodesList().size();
				//
				// if (fatherNode.getNodeType().contains(
				// "android.support.v4.view.ViewPager")) {
				// nodesListCount -= 1;
				// }

				for (int k = 0; k < nodeList.get(i).getNodesList().size(); k++) {

					if (!nodeList.get(i).getActivityName()
							.equals(this.getCurrentActivityName())
							|| !isMenuShow()) {
						// 如果不一致则按照节点序列进行点击
						Node node = nodeList.get(i).getNodesList().get(k);
						if (node.getActivityName().equals(
								this.getCurrentActivityName())) {
							if (node.isScrollable()) {
								if (node.getNodeType().contains(
										"android.support.v4.view.ViewPager")) {
									try {
										Process process = Runtime
												.getRuntime()
												.exec("adb"
														+ deviceSerial
														+ " shell input swipe "
														+ (node.getLocationX()
																+ node.getWidth() - 5)
														+ " "
														+ (node.getLocationY() + nodeList
																.get(i)
																.getHeight() / 2)
														+ " "
														+ (node.getLocationX() + 5)
														+ " "
														+ (node.getLocationY() + nodeList
																.get(i)
																.getHeight() / 2));
										BufferedReader bufferedReader = new BufferedReader(
												new InputStreamReader(process
														.getInputStream()));
										String line = "";
										while ((line = bufferedReader
												.readLine()) != null) {
										}
										try {
											Thread.sleep(1500);
										} catch (InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}

										System.out
												.println("adb"
														+ deviceSerial
														+ " shell input swipe "
														+ (node.getLocationX()
																+ nodeList
																		.get(i)
																		.getWidth() - 5)
														+ " "
														+ (node.getLocationY() + nodeList
																.get(i)
																.getHeight() / 2)
														+ " "
														+ (node.getLocationX() + 5)
														+ " "
														+ (node.getLocationY() + nodeList
																.get(i)
																.getHeight() / 2));

									} catch (IOException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}

								} else {
									try {
										Process process = Runtime
												.getRuntime()
												.exec("adb"
														+ deviceSerial
														+ " shell input swipe "
														+ (node.getLocationX() + node
																.getWidth() / 2)
														+ " "
														+ (node.getLocationY()
																+ node.getHeight() - 5)
														+ " "
														+ (node.getLocationX() + node
																.getWidth() / 2)
														+ " "
														+ (node.getLocationY() + 5));
										BufferedReader bufferedReader = new BufferedReader(
												new InputStreamReader(process
														.getInputStream()));
										String line = "";
										while ((line = bufferedReader
												.readLine()) != null) {
										}
										try {
											Thread.sleep(1500);
										} catch (InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}

										System.out.println("adb"
												+ deviceSerial
												+ " shell input swipe "
												+ (node.getLocationX() + node
														.getWidth() / 2)
												+ " "
												+ (node.getLocationY()
														+ node.getHeight() - 5)
												+ " "
												+ (node.getLocationX() + node
														.getWidth() / 2)
												+ " "
												+ (nodeList.get(i)
														.getLocationY() + 5));

									} catch (IOException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
								}

							} else {
								try {
									Process process = Runtime.getRuntime()
											.exec("adb" + deviceSerial
													+ " shell input tap "
													+ node.getX() + " "
													+ node.getY());
									BufferedReader bufferedReader = new BufferedReader(
											new InputStreamReader(
													process.getInputStream()));
									String line = "";
									while ((line = bufferedReader.readLine()) != null) {
									}
									try {
										Thread.sleep(1500);
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

								} catch (IOException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}
							}

						}

						if (nodeList.get(i).getActivityName()
								.equals(this.getCurrentActivityName())) {
							if (!nodeList.get(i).isFromMenu()) {
								break;
							} else {
								if (isMenuShow()) {
									break;
								}
							}
						}
					} else {

						// 如果一致退出循环
						break;
					}
				}
			}

			System.out.println("Top:" + top + ",this current test activity:"
					+ nodeList.get(0).getActivityName() + " views: "
					+ nodeList.size());

			// 首次获取点击前当前应用所处activity的名称
			String activityName1 = this.getCurrentActivityName();

			Boolean isViewPager = false;

			// 进入循环体后首先针对每个节点进行点击操作
			try {

				// 目前该方法支持的Android4.1以上
				if (top < testDepth) {
					if (nodeList.get(i).isScrollable()) {

						if (nodeList.get(i).getNodeType()
								.contains("android.support.v4.view.ViewPager")) {
							isViewPager = true;
							try {
								Process process = Runtime
										.getRuntime()
										.exec("adb"
												+ deviceSerial
												+ " shell input swipe "
												+ (nodeList.get(i)
														.getLocationX()
														+ nodeList.get(i)
																.getWidth() - 5)
												+ " "
												+ (nodeList.get(i)
														.getLocationY() + nodeList
														.get(i).getHeight() / 2)
												+ " "
												+ (nodeList.get(i)
														.getLocationX() + 5)
												+ " "
												+ (nodeList.get(i)
														.getLocationY() + nodeList
														.get(i).getHeight() / 2));
								BufferedReader bufferedReader = new BufferedReader(
										new InputStreamReader(
												process.getInputStream()));
								String line = "";
								while ((line = bufferedReader.readLine()) != null) {
								}

								System.out
										.println("adb"
												+ deviceSerial
												+ " shell input swipe "
												+ (nodeList.get(i)
														.getLocationX()
														+ nodeList.get(i)
																.getWidth() - 5)
												+ " "
												+ (nodeList.get(i)
														.getLocationY() + nodeList
														.get(i).getHeight() / 2)
												+ " "
												+ (nodeList.get(i)
														.getLocationX() + 5)
												+ " "
												+ (nodeList.get(i)
														.getLocationY() + nodeList
														.get(i).getHeight() / 2));

							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}

						} else {
							try {
								Process process = Runtime
										.getRuntime()
										.exec("adb"
												+ deviceSerial
												+ " shell input swipe "
												+ (nodeList.get(i)
														.getLocationX() + nodeList
														.get(i).getWidth() / 2)
												+ " "
												+ (nodeList.get(i)
														.getLocationY()
														+ nodeList.get(i)
																.getHeight() - 5)
												+ " "
												+ (nodeList.get(i)
														.getLocationX() + nodeList
														.get(i).getWidth() / 2)
												+ " "
												+ (nodeList.get(i)
														.getLocationY() + 5));
								BufferedReader bufferedReader = new BufferedReader(
										new InputStreamReader(
												process.getInputStream()));
								String line = "";
								while ((line = bufferedReader.readLine()) != null) {
								}

								System.out
										.println("adb"
												+ deviceSerial
												+ " shell input swipe "
												+ (nodeList.get(i)
														.getLocationX() + nodeList
														.get(i).getWidth() / 2)
												+ " "
												+ (nodeList.get(i)
														.getLocationY()
														+ nodeList.get(i)
																.getHeight() - 5)
												+ " "
												+ (nodeList.get(i)
														.getLocationX() + nodeList
														.get(i).getWidth() / 2)
												+ " "
												+ (nodeList.get(i)
														.getLocationY() + 5));

							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}

						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						adbManger.getDeviceZoomImage(device,
								apkParentPath + "/screenshot" + serial + "/"
										+ System.currentTimeMillis());
						/**
						 * 此处可以判断两处的图片的误差值是否很大，如果很大则说明滑动效果不明显，
						 */

					} else {

						Process process = Runtime.getRuntime().exec(
								"adb" + deviceSerial + " shell input tap "
										+ nodeList.get(i).getX() + " "
										+ nodeList.get(i).getY());
						BufferedReader bufferedReader = new BufferedReader(
								new InputStreamReader(process.getInputStream()));
						String line = "";
						while ((line = bufferedReader.readLine()) != null) {
						}
						System.out.println("click top " + top + "-" + i
								+ " location(" + nodeList.get(i).getX() + ","
								+ nodeList.get(i).getY() + ")");
						try {
							Thread.sleep(2000);
							if (!isKeyboardHide()) {
								System.out
										.println("Keyboard have show!----------------------");
								Runtime.getRuntime().exec(
										"adb" + deviceSerial
												+ " shell input keyevent 4");
							}
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						adbManger.getDeviceZoomImage(device,
								apkParentPath + "/screenshot" + serial + "/"
										+ System.currentTimeMillis());
					}

				} else if (top >= testDepth) {

					break;
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// 获取点击后当前应用所处activity名称
			String activityName2 = this.getCurrentActivityName();

			// 此处针对当前应用是否被其他应用启动而不再最上层做了检测，策略：1.如果当前应用被其他应用覆盖，可点击back键2次每次都做检测。2.如果超过2次直接关闭当前覆盖的应用。
			for (int m = 0; m < 3; m++) {
				currentPackageName = this.getCurrentPackAgeName();
				if (!currentPackageName.contains(packageName)) {
					System.out.println(currentPackageName);
					System.out.println(packageName);
					if (m == 2) {
						if (top > 0) {
							finishOpenedActivities(currentPackageName);
							break;
						}
					}
					try {
						System.out.println("Jump to other app :"
								+ currentPackageName);
						Runtime.getRuntime().exec(
								"adb" + deviceSerial
										+ " shell input keyevent 4");
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					break;
				}
			}

			// 在检测完是否被其他应用覆盖后再次获取一下当前的activity,以供程序对比是否点击发生了变化
			activityName2 = this.getCurrentActivityName();

			// 判断当前activity是否和点击前一致，如果一致说明UI没有作跳转
			if (!activityName2.equals(activityName1)) {

				// 界面发生了跳转
				System.out.println("go new activity" + activityName2);

				// 此处判断是否进入的新界面是否是父界面
				if (!activityName2.equals(fatherNode.getActivityName())) {

					// 没有进入父界面，进入了一个新的界面
					// 此处判断测试深度，以免深度过深测试时间过长
					if (top >= testDepth) {
						break;
					}

					// 获取新的uidump.xml文件并生成
					this.getUiHierarchyFile(
							device,
							new File(System.getProperty("user.home")
									+ System.getProperty("file.separator")
									+ "uidump" + serial + ".xml"));
					File inputXml = new File(System.getProperty("user.home")
							+ System.getProperty("file.separator") + "uidump"
							+ serial + ".xml");
					List<Element> all = this.getAllLeafNode(inputXml);

					ArrayList<Node> newNodeList = getNodesList(
							this.getAllElement(all, inputXml), nodeList.get(i)
									.getNodesList());

					if (this.isTraversal(activityName2)) {
						System.out.println("is traversal");
						try {
							Runtime.getRuntime().exec(
									"adb" + deviceSerial
											+ " shell input keyevent 4");
							Thread.sleep(2000);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						continue;
					} else {
						// 在获取完节点信息后开始递归调用自身方法
						System.out.println("is not traversl");
						// 此部分为了判断当前节点所处得activity是否和当前应用所在得activity一致,如果不一致从节点路径点击回到当前节点所处的activity

						if (isRecentActivity(nodeList.get(i).getNodesList(),
								this.getCurrentActivityName())) {
							continue;
						} else {
							forEachViews(newNodeList, top + 1, nodeList.get(i));
						}

					}

					// 如果递归调用完成打印出那一层测试结束
					System.out.println("Top " + (top + 1) + " finsh");

					// 此部分为了判断当递归调用完成后，当前节点所处得activity是否和当前应用所在得activity一致,如果不一致从节点路径点击回到当前节点所处的activity
					if (!nodeList.get(i).getActivityName()
							.equals(this.getCurrentActivityName())) {
						this.finishOpenedActivities();
						this.startLauncherActivity();
						for (int k = 0; k < nodeList.get(i).getNodesList()
								.size(); k++) {
							if (!nodeList.get(i).getActivityName()
									.contains(this.getCurrentActivityName())) {
								Node node = nodeList.get(i).getNodesList()
										.get(k);
								if (node.getActivityName().equals(
										this.getCurrentActivityName())) {
									try {
										Process process = Runtime.getRuntime()
												.exec("adb" + deviceSerial
														+ " shell input tap "
														+ node.getX() + " "
														+ node.getY());
										BufferedReader bufferedReader = new BufferedReader(
												new InputStreamReader(process
														.getInputStream()));
										String line = "";
										while ((line = bufferedReader
												.readLine()) != null) {
										}
										try {
											Thread.sleep(1500);
										} catch (InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									} catch (IOException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}

								}
							} else
								break;
						}
					}

					// 再次判断是否在递归完成后又进入了父界面，如果进入重新进入当前节点所处界面
					if (this.getCurrentActivityName().equals(
							fatherNode.getActivityName())) {
						try {
							System.out.println("re enther");
							Process process = Runtime.getRuntime().exec(
									"adb" + deviceSerial + " shell input tap "
											+ fatherNode.getX() + " "
											+ fatherNode.getY());
							BufferedReader bufferedReader = new BufferedReader(
									new InputStreamReader(
											process.getInputStream()));
							String line = "";
							while ((line = bufferedReader.readLine()) != null) {
							}
							try {
								Thread.sleep(1500);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							continue;
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				} else if (activityName2.equals(fatherNode.getActivityName())) {

					// 判断点击进入新界面后判断是否进入了父界面，如果进入重新进入当前节点所处界面
					System.out.println("back to top:" + (top - 1)
							+ " father activity:"
							+ fatherNode.getActivityName());
					try {
						System.out.println("enter father " + "adb"
								+ deviceSerial + " shell input tap "
								+ fatherNode.getX() + " " + fatherNode.getY());
						Process process = Runtime.getRuntime().exec(
								"adb" + deviceSerial + " shell input tap "
										+ fatherNode.getX() + " "
										+ fatherNode.getY());
						BufferedReader bufferedReader = new BufferedReader(
								new InputStreamReader(process.getInputStream()));
						String line = "";
						while ((line = bufferedReader.readLine()) != null) {
						}
						try {
							Thread.sleep(1500);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						continue;
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} else if (activityName1.equals(activityName2) && isMenuShow()) {

				// 界面没有发生改变但是有弹出menu菜单
				System.out.println("show menu");
				// 此处判断测试深度，以免深度过深测试时间过长
				if (top >= testDepth) {
					break;
				}

				// 获取新的uidump.xml文件并生成
				this.getUiHierarchyFile(
						device,
						new File(System.getProperty("user.home")
								+ System.getProperty("file.separator")
								+ "uidump" + serial + ".xml"));
				File inputXml = new File(System.getProperty("user.home")
						+ System.getProperty("file.separator") + "uidump"
						+ serial + ".xml");
				List<Element> all = this.getAllLeafNode(inputXml);

				ArrayList<Node> newNodeList = getNodesList(all, nodeList.get(i)
						.getNodesList(), true);

				if (this.isTraversal(activityName2)) {
					System.out.println("is traversal");
					continue;
				} else {
					// 在获取完节点信息后开始递归调用自身方法
					System.out.println("is not traversl");
					forEachViews(newNodeList, top + 1, nodeList.get(i));
				}

				// 如果递归调用完成打印出那一层测试结束
				System.out.println("Top " + (top + 1) + " finsh");

				// 此部分为了判断当递归调用完成后，当前节点所处得activity是否和当前应用所在得activity一致,如果不一致从节点路径点击回到当前节点所处的activity
				if (!nodeList.get(i).getActivityName()
						.equals(this.getCurrentActivityName())) {
					this.finishOpenedActivities();
					this.startLauncherActivity();
					for (int k = 0; k < nodeList.get(i).getNodesList().size(); k++) {
						if (!nodeList.get(i).getActivityName()
								.contains(this.getCurrentActivityName())) {
							Node node = nodeList.get(i).getNodesList().get(k);
							if (node.getActivityName().equals(
									this.getCurrentActivityName())) {
								try {
									Process process = Runtime.getRuntime()
											.exec("adb" + deviceSerial
													+ " shell input tap "
													+ node.getX() + " "
													+ node.getY());
									BufferedReader bufferedReader = new BufferedReader(
											new InputStreamReader(
													process.getInputStream()));
									String line = "";
									while ((line = bufferedReader.readLine()) != null) {
									}
									try {
										Thread.sleep(1500);
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								} catch (IOException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}

							}
						} else
							break;
					}
				}

				// 再次判断是否在递归完成后又进入了父界面，如果进入重新进入当前节点所处界面
				if (this.getCurrentActivityName().equals(
						fatherNode.getActivityName())) {
					try {
						System.out.println("re enther");
						Process process = Runtime.getRuntime().exec(
								"adb" + deviceSerial + " shell input tap "
										+ fatherNode.getX() + " "
										+ fatherNode.getY());
						BufferedReader bufferedReader = new BufferedReader(
								new InputStreamReader(process.getInputStream()));
						String line = "";
						while ((line = bufferedReader.readLine()) != null) {
						}
						try {
							Thread.sleep(1500);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						continue;
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			} else if (activityName1.equals(activityName2) && isViewPager) {

				// 界面没有发生改变但是有弹出menu菜单
				System.out.println("from ViewPager");
				// 此处判断测试深度，以免深度过深测试时间过长
				if (top >= testDepth) {
					break;
				}

				// 获取新的uidump.xml文件并生成
				this.getUiHierarchyFile(
						device,
						new File(System.getProperty("user.home")
								+ System.getProperty("file.separator")
								+ "uidump" + serial + ".xml"));
				File inputXml = new File(System.getProperty("user.home")
						+ System.getProperty("file.separator") + "uidump"
						+ serial + ".xml");
				List<Element> all = this.getAllLeafNode(inputXml);

				ArrayList<Node> newNodeList = getNodesList(all, nodeList.get(i)
						.getNodesList(), true);

				if (this.isTraversal(activityName2)) {
					System.out.println("is traversal");
					continue;
				} else {
					// 在获取完节点信息后开始递归调用自身方法
					System.out.println("is not traversl");
					forEachViews(newNodeList, top, nodeList.get(i));
				}

				// 如果递归调用完成打印出那一层测试结束
				System.out.println("Top " + top + " finsh");

			} else {

				// 此部分逻辑是点击后界面没有作跳转还在当前界面
				try {

					// 判断当前界面是否有对话框覆盖，如有点击back键取消
					if (top != 0 && isDialog()) {
						Runtime.getRuntime().exec(
								"adb" + deviceSerial
										+ " shell input keyevent 4");
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if (isDialog()) {
							Runtime.getRuntime().exec(
									"adb" + deviceSerial
											+ " shell input keyevent 4");
						}
					} else {

						// 如果没有则检测界面是否又微型的变化，此部分还在作优化代码暂时保留
						// //获取新的uidumpxml文件并生成
						// this.getUiHierarchyFile(device,
						// new File(System.getProperty("user.home")
						// + System.getProperty("file.separator")
						// + "uidump"+serial+".xml"));
						// File inputXml = new File(
						// System.getProperty("user.home")
						// + System.getProperty("file.separator")
						// + "uidump"+serial+".xml");
						// List<Element> all = this.getAllLeafNode(inputXml);
						// System.out.println("old:" + nodeList.size());
						// System.out.println("new:" + all.size());
						// if (all.size() != nodeList.size()) {
						// System.out.println("hava a Dailog");
						// ArrayList<Node> nodeList2 = this.getNodesList(all);
						// StringBuffer sb = new StringBuffer();
						// for (int j = 0; j < nodeList.size(); j++) {
						// sb.append(nodeList.get(j).getX());
						// sb.append(nodeList.get(j).getY());
						// }
						// System.out.println(sb.toString());
						// StringBuffer sb2 = new StringBuffer();
						// for (int j = 0; j < nodeList2.size(); j++) {
						// sb2.append(nodeList2.get(j).getX());
						// sb2.append(nodeList2.get(j).getY());
						// }
						// System.out.println(sb2.toString());
						// float similarityRatio = getSimilarityRatio(
						// sb2.toString(), sb.toString());
						// System.out.println("SimilarityRatio:"
						// + similarityRatio);
						//
						// // for (int i = 0; i < nodeList2.size(); i++) {
						// // for (int j = 0; j < nodeList1.size(); j++) {
						// // if (nodeList2.get(i).getX() ==
						// // nodeList1.get(j).getX()
						// // && nodeList2.get(i).getY() == nodeList1.get(j)
						// // .getY()) {
						// //
						// // }
						// // }
						// // }
						//
						// try {
						// if (top != 0 && similarityRatio < 0.6) {
						//
						// Runtime.getRuntime().exec(
						// "adb" + deviceSerial + " shell input keyevent 4");
						// try {
						// Thread.sleep(2000);
						// } catch (InterruptedException e1) {
						// // TODO Auto-generated catch block
						// e1.printStackTrace();
						// }
						// activityName2 = this
						// .getCurrentActivityName();
						// if (activityName2.contains(fatherNode
						// .getActivityName())) {
						// System.out.println("back to top:"
						// + (top - 1)
						// + " father activity:"
						// + fatherNode.getActivityName());
						//
						// try {
						// System.out.println("enter father "
						// + "adb" + deviceSerial + " shell input tap "
						// + fatherNode.getX() + " "
						// + fatherNode.getY());
						// Process process = Runtime
						// .getRuntime()
						// .exec("adb" + deviceSerial + " shell input tap "
						// + fatherNode.getX()
						// + " "
						// + fatherNode.getY());
						// BufferedReader bufferedReader = new BufferedReader(
						// new InputStreamReader(
						// process.getInputStream()));
						// String line = "";
						// while ((line = bufferedReader
						// .readLine()) != null) {
						// }
						// try {
						// Thread.sleep(1500);
						// } catch (InterruptedException e) {
						// // TODO Auto-generated catch
						// // block
						// e.printStackTrace();
						// }
						// continue;
						// } catch (IOException e) {
						// // TODO Auto-generated catch block
						// e.printStackTrace();
						// }
						// }
						//
						// }
						//
						// } catch (IOException e) {
						// // TODO Auto-generated catch block
						// e.printStackTrace();
						// }
						// }
					}

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

		}

		if (!this.isTraversal(nodeList.get(0).getActivityName())
				&& !nodeList.get(0).isFromMenu()) {
			if (!nodeList.get(0).getActivityName().equals(mainActivityName)) {
				activityNameList.add(nodeList.get(0).getActivityName());
			}
		}
		// 当循环遍历结束后需要检测是否正常回到了父界面，如果没有则点击back键回到父界面
		if (top != 0 && !this.getCurrentActivityName().equals(mainActivityName)) {
			try {
				System.out.println("Current top:" + top);
				for (int k = 0; k < 2; k++) {
					if (!this.getCurrentActivityName().equals(
							fatherNode.activityName)) {

						if (!this.getCurrentActivityName().equals(
								mainActivityName)) {
							System.out.println("CurrentActivity:"
									+ this.getCurrentActivityName());
							System.out.println("FatherNodeActivity:"
									+ fatherNode.getActivityName());
							System.out.println("adb" + deviceSerial
									+ " shell input keyevent 4");
							Runtime.getRuntime().exec(
									"adb" + deviceSerial
											+ " shell input keyevent 4");
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

					} else
						break;
				}

				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return true;
	}

	/**
	 * 获取当前activity名称
	 * 
	 * @return
	 */
	public String getCurrentActivityName() {
		String activityName = "";
		try {

			Process process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell dumpsys activity");
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			boolean isrunning = true;
			while (isrunning) {
				if ((line = bufferedReader.readLine()) != null) {
					if (line.contains("mFocusedActivity")) {
						int beginIndex = line.indexOf("/");
						int endIndex = line.indexOf("}");
						activityName = line.substring(beginIndex + 1, endIndex);
						if (activityName.contains(" ")) {
							endIndex = activityName.indexOf(" ");
							activityName = activityName.substring(0, endIndex);
						}
					}
				} else {
					isrunning = false;
					process.destroy();
				}
			}

		} catch (Exception e) {
			// TODO: handle exception
		}

		return activityName;
	}

	/**
	 * 重启ADB
	 */
	public void restartADB() {
		try {

			Process process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " kill-server");
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				System.out.println(line);
			}
			process.destroy();
			Thread.sleep(3000);
			process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " start-server");
			bufferedReader = new BufferedReader(new InputStreamReader(
					process.getInputStream()));
			while ((line = bufferedReader.readLine()) != null) {
				System.out.println(line);
			}
			process.destroy();
			Thread.sleep(3000);
			process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " devices");
			bufferedReader = new BufferedReader(new InputStreamReader(
					process.getInputStream()));
			while ((line = bufferedReader.readLine()) != null) {
				System.out.println(line);
			}
			process.destroy();
			Thread.sleep(3000);

		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	/**
	 * 获得当前系统当前可用连接的android设备
	 */
	public ArrayList<String> getDevices() {

		ArrayList<String> arrayList = new ArrayList<String>();
		int count = 0;
		try {
			Process devicesProcess = Runtime.getRuntime().exec("adb devices");
			BufferedReader devicesBufferedReader = new BufferedReader(
					new InputStreamReader(devicesProcess.getInputStream()));
			String devicesline;
			boolean isrunning = true;

			while (isrunning) {
				if ((devicesline = devicesBufferedReader.readLine()) != null) {
					if (!devicesline
							.contains("daemon not running. starting it now on port 5037")
							&& !devicesline
									.contains("daemon started successfully")
							&& !devicesline
									.contains("List of devices attached")) {
						arrayList.add(devicesline);
						count += count;

					}

					// if (logcatline.contains("E/ActivityManager")
					// && logcatline.contains("ANR")) {
					// failure = getStackTraceLine(logcatline) + "\n";
					// failMessage = failure;
					// }
				} else {
					isrunning = false;
					devicesProcess.destroy();
				}
			}

		} catch (Exception e) {
			// TODO: handle exception
		}
		return arrayList;
	}

	/**
	 * 获取当前运行包的包名
	 * 
	 * @return
	 */
	public String getCurrentPackAgeName() {
		String packageName = "";
		try {

			Process process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell dumpsys activity");
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			boolean isrunning = true;
			while (isrunning) {
				if ((line = bufferedReader.readLine()) != null) {
					if (line.contains("mFocusedActivity")) {
						int beginIndex = line.indexOf("{");
						line = line.substring(beginIndex, line.length());
						beginIndex = line.indexOf(" ");
						line = line.substring(beginIndex + 1, line.length());
						beginIndex = line.indexOf(" ");
						int endIndex = line.indexOf("/");
						line = line.substring(beginIndex + 1, endIndex);
						packageName = line;
					}
				} else {
					isrunning = false;
					process.destroy();
				}
			}

		} catch (Exception e) {
			// TODO: handle exception
		}

		return packageName;
	}

	/**
	 * 比较两个字符串的有多少个不同之处
	 * 
	 * @param str
	 * @param target
	 * @return
	 */
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

	/**
	 * 取三个数中的最小数
	 * 
	 * @param one
	 * @param two
	 * @param three
	 * @return
	 */
	private int min(int one, int two, int three) {
		return (one = one < two ? one : two) < three ? one : three;
	}

	/**
	 * 获取两字符串的相似度
	 * 
	 * @param str
	 * @param target
	 * @return
	 */
	public float getSimilarityRatio(String str, String target) {

		return 1 - (float) compare(str, target)
				/ Math.max(str.length(), target.length());
	}

	/**
	 * 获取两字符串的相似度
	 * 
	 * @param str
	 * @param target
	 * @return
	 */
	public List<Element> getAllElement(List<Element> list, File inputxml) {

		List<Element> allList = this.getAllNode(inputxml);
		for (int i = 0; i < allList.size(); i++) {
			if (allList.get(i).attributeValue("scrollable").contains("true")) {
				System.out.println(allList.get(i).attributeValue("class"));
				list.add(allList.get(i));
			}
		}

		return list;
	}

	public boolean isRecentActivity(ArrayList<Node> arrayList,
			String activityName) {
		boolean bool = false;
		for (int i = 0; i < arrayList.size(); i++) {
			Node node = arrayList.get(i);
			if (node.getActivityName().equals(activityName)) {
				bool = true;
			}
		}
		return bool;
	}

	/**
	 * 判断当前界面是否有对话框存在
	 * 
	 * @return
	 */
	public boolean isDialog() {
		int i = 0;
		boolean bool = false;
		String str = this.getCurrentActivityName();
		try {
			Process process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell dumpsys SurfaceFlinger");
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			boolean isrunning = true;
			while ((line = bufferedReader.readLine()) != null) {
				if (line.contains(str)) {
					i++;
				}
			}
			process.destroy();
			System.out.println("i=" + i);
			if (i >= 4) {
				bool = true;
			}

		} catch (Exception e) {
			// TODO: handle exception
		}
		return bool;

	}

	public boolean isKeyboardHide() {
		boolean bool = true;
		try {
			Process process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell dumpsys window windows");
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			boolean flag = false;
			while ((line = bufferedReader.readLine()) != null) {
				if (line.contains("Window #") && line.contains("InputMethod")) {
					flag = true;
				} else if (line.contains("Window #")) {
					flag = false;
				}
				if (flag) {
					if (line.contains("shown=true")) {
						bool = false;
					}
				}
			}
			process.destroy();

		} catch (Exception e) {
			// TODO: handle exception
		}
		return bool;
	}

	public boolean isMenuShow() {
		boolean bool = false;
		try {
			Process process = Runtime.getRuntime().exec(
					"adb" + deviceSerial + " shell dumpsys window windows");
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				if (line.contains("AtchDlg")
						|| (line.contains("PopupWindow") && line
								.contains("mCurrentFocus"))) {
					bool = true;
					break;
				}
			}
			process.destroy();

		} catch (Exception e) {
			// TODO: handle exception
		}
		return bool;
	}

	/**
	 * 当命令参数出错时打印使用帮助
	 */
	private static void printUsageAndQuit() {
		// 80 cols marker:
		// 01234567890123456789012345678901234567890123456789012345678901234567890123456789
		System.out.println("Usage: Grapes [path] [-s | - p | -t| -f]");
		System.out.println("");
		System.out.println("    path    Uses project path.");
		System.out.println("    -s      the device or emulator serial number.");
		System.out.println("    -p      Uses the user case project id.");
		System.out.println("    -t      Uses the user case project type.");
		System.out.println("    -f      Uses the Report Summary");
		System.out.println("");

		System.exit(1);
	}

	/**
	 * 打印出消息并退出程序
	 * 
	 * @param message
	 * @param terminate
	 */
	private static void printAndExit(String message, boolean terminate) {
		System.out.println(message);
		System.exit(1);
	}
}
