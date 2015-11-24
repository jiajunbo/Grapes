package com.nq.auto;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 
 * @author jiakui
 * 
 */
public class SearchFile {
	public ArrayList arraylist = new ArrayList();

	public ArrayList view(String filePath) throws IOException {
		ArrayList<String> filesNames = findAllFiles(filePath);
		return this.sortFileName(filesNames);
	}

	public ArrayList sortFileName(ArrayList<String> filesNames) {

		String[] filesName = filesNames.toArray(new String[filesNames.size()]);
		SortFile sortFile = new SortFile();
		sortFile.sortedByName(filesName);
		ArrayList list = new ArrayList();
		for (int j = 0; j < filesName.length; j++) {
			list.add(filesName[j]);
		}
		return list;

	}

	public ArrayList findAllFiles(String filePath) throws IOException {
		File file = new File(filePath);
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (int i = 0; i < files.length; ++i)
				if (files[i].isDirectory())
					findAllFiles(filePath
							+ System.getProperty("file.separator")
							+ files[i].getName());
				else
					this.arraylist.add(file.getAbsolutePath()
							+ System.getProperty("file.separator")
							+ files[i].getName());
		} else {
			this.arraylist.add(file.getAbsolutePath()
					+ System.getProperty("file.separator") + file.getName());
		}

		return this.arraylist;
	}
}
