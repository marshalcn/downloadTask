package com.example.download.manager;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import com.example.download.model.DownloadTaskInfo;

/**
 * 任务管理器类，用于管理下载任务的历史记录
 */
public class TaskManager {
    private static final String TASKS_FILE = System.getProperty("user.home") + File.separator + "download_tasks.dat";
    private List<DownloadTaskInfo> allTasks = new ArrayList<>();
    
    /**
     * 构造函数，加载历史任务
     */
    public TaskManager() {
        loadTasks();
    }
    
    /**
     * 添加新任务
     * 
     * @param taskInfo 任务信息
     */
    public void addTask(DownloadTaskInfo taskInfo) {
        allTasks.add(taskInfo);
        saveTasks();
    }
    
    /**
     * 更新任务信息
     * 
     * @param taskInfo 任务信息
     */
    public void updateTask(DownloadTaskInfo taskInfo) {
        for (int i = 0; i < allTasks.size(); i++) {
            if (allTasks.get(i).getId().equals(taskInfo.getId())) {
                allTasks.set(i, taskInfo);
                saveTasks();
                return;
            }
        }
    }
    
    /**
     * 获取所有任务
     * 
     * @return 任务列表
     */
    public List<DownloadTaskInfo> getAllTasks() {
        return new ArrayList<>(allTasks);
    }
    
    /**
     * 获取已完成的任务（按完成时间降序排序）
     * 
     * @return 已完成任务列表
     */
    public List<DownloadTaskInfo> getCompletedTasks() {
        return allTasks.stream()
                .filter(task -> task.getStatus() == DownloadTaskInfo.TaskStatus.COMPLETED)
                .sorted(Comparator.comparing(DownloadTaskInfo::getCompletedTime).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * 获取未完成的任务（按添加时间降序排序）
     * 
     * @return 未完成任务列表
     */
    public List<DownloadTaskInfo> getUncompletedTasks() {
        return allTasks.stream()
                .filter(task -> task.getStatus() != DownloadTaskInfo.TaskStatus.COMPLETED)
                .sorted(Comparator.comparing(DownloadTaskInfo::getAddTime).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * 获取正在下载的任务
     * 
     * @return 正在下载任务列表
     */
    public List<DownloadTaskInfo> getDownloadingTasks() {
        return allTasks.stream()
                .filter(task -> task.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING)
                .collect(Collectors.toList());
    }
    
    /**
     * 按要求排序所有任务：
     * 1. 已完成的任务按完成时间降序排序
     * 2. 未完成的任务按添加时间降序排序
     * 
     * @return 排序后的任务列表
     */
    public List<DownloadTaskInfo> getSortedTasks() {
        List<DownloadTaskInfo> completedTasks = getCompletedTasks();
        List<DownloadTaskInfo> uncompletedTasks = getUncompletedTasks();
        
        List<DownloadTaskInfo> sortedTasks = new ArrayList<>();
        sortedTasks.addAll(completedTasks);
        sortedTasks.addAll(uncompletedTasks);
        
        return sortedTasks;
    }
    
    /**
     * 删除任务
     * 
     * @param taskId 任务ID
     */
    public void deleteTask(String taskId) {
        allTasks.removeIf(task -> task.getId().equals(taskId));
        saveTasks();
    }
    
    /**
     * 保存任务列表到文件
     */
    private void saveTasks() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(TASKS_FILE))) {
            oos.writeObject(allTasks);
        } catch (IOException e) {
            System.err.println("保存任务列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 从文件加载任务列表
     */
    private void loadTasks() {
        File file = new File(TASKS_FILE);
        if (!file.exists()) {
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(TASKS_FILE))) {
            allTasks = (List<DownloadTaskInfo>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("加载任务列表失败: " + e.getMessage());
            allTasks = new ArrayList<>();
        }
    }
}