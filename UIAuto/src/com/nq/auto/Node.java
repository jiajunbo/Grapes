package com.nq.auto;

import java.util.ArrayList;

/**
 * 界面节点定义用于处理每个节点的路径存储
 * 
 * @author jiakui
 * 
 */
public class Node {

	/**
	 * 定义节点可点击x位置
	 */
	public int x;

	/**
	 * 定义该节点存储由主界面activity到当前节点所在activity需要点击的节点序列
	 */
	public ArrayList<Node> nodesList;

	/**
	 * 获取节点序列列表
	 * 
	 * @return
	 */
	public ArrayList<Node> getNodesList() {
		return nodesList;
	}

	/**
	 * 设置节点列表
	 * 
	 * @param nodesList
	 */
	public void setNodesList(ArrayList<Node> nodesList) {
		this.nodesList = nodesList;
	}

	/**
	 * 当前节点的activity的名称
	 */
	public String activityName = "";

	/**
	 * 获取当前节点的activity名称
	 * 
	 * @return
	 */
	public String getActivityName() {
		return activityName;
	}

	/**
	 * 设置当前节点的activity名称
	 * 
	 * @param activityName
	 */
	public void setActivityName(String activityName) {
		this.activityName = activityName;
	}

	/**
	 * 获得当前节点中心点的x轴坐标
	 * 
	 * @return
	 */
	public int getX() {
		return x;
	}

	/**
	 * 设置当前节点中心点x轴坐标
	 * 
	 * @param x
	 */
	public void setX(int x) {
		this.x = x;
	}

	/**
	 * 获取当前节点y轴的中心点的坐标
	 * 
	 * @return
	 */
	public int getY() {
		return y;
	}

	/**
	 * 设置当前节点y轴的中心点坐标
	 * 
	 * @param y
	 */
	public void setY(int y) {
		this.y = y;
	}

	/**
	 * 定义节点可点击y位置
	 */
	public int y;

	public boolean isFromMenu = false;

	public boolean isScrollable = false;

	public boolean isScrollable() {
		return isScrollable;
	}

	public void setScrollable(boolean isScrollable) {
		this.isScrollable = isScrollable;
	}

	public boolean isFromMenu() {
		return isFromMenu;
	}

	public void setFromMenu(boolean isFromMenu) {
		this.isFromMenu = isFromMenu;
	}

	public int locationX;

	public int getLocationX() {
		return locationX;
	}

	public void setLocationX(int locationX) {
		this.locationX = locationX;
	}

	public int getLocationY() {
		return locationY;
	}

	public void setLocationY(int locationY) {
		this.locationY = locationY;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public int locationY;

	public int width;

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int height;

	public String nodeType;

	public String getNodeType() {
		return nodeType;
	}

	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	
}
