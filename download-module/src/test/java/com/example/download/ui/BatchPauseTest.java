package com.example.download.ui;

import com.example.download.core.MultiThreadDownloader;
import com.example.download.manager.TaskManager;
import com.example.download.model.DownloadTaskInfo;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class BatchPauseTest {

    public static void main(String[] args) {
        // 创建测试对象
        MultiThreadDownloader downloader = new MultiThreadDownloader();
        TaskManager taskManager = new TaskManager();
        
        // 创建测试任务
        DownloadTaskInfo task1 = new DownloadTaskInfo();
        task1.setFileName("test1.txt");
        task1.setStatus(DownloadTaskInfo.TaskStatus.DOWNLOADING);
        
        DownloadTaskInfo task2 = new DownloadTaskInfo();
        task2.setFileName("test2.txt");
        task2.setStatus(DownloadTaskInfo.TaskStatus.DOWNLOADING);
        
        DownloadTaskInfo task3 = new DownloadTaskInfo();
        task3.setFileName("test3.txt");
        task3.setStatus(DownloadTaskInfo.TaskStatus.WAITING);
        
        // 添加任务到任务管理器
        taskManager.addTask(task1);
        taskManager.addTask(task2);
        taskManager.addTask(task3);
        
        // 模拟选择任务
        Set<String> selectedTaskIds = new HashSet<>();
        selectedTaskIds.add(task1.getId());
        selectedTaskIds.add(task3.getId());
        
        System.out.println("任务1 ID: " + task1.getId());
        System.out.println("任务2 ID: " + task2.getId());
        System.out.println("任务3 ID: " + task3.getId());
        System.out.println("选中的任务IDs: " + selectedTaskIds);
        
        System.out.println("批量暂停功能测试开始...");
        System.out.println("\n测试1: 选择正在下载的任务(test1)和等待中的任务(test3)");
        
        // 检查任务初始状态
        System.out.println("初始状态:");
        System.out.println("task1状态: " + task1.getStatus());
        System.out.println("task2状态: " + task2.getStatus());
        System.out.println("task3状态: " + task3.getStatus());
        
        // 测试批量暂停逻辑
        boolean hasTasksToPause = false;
        for (DownloadTaskInfo task : taskManager.getAllTasks()) {
            if (selectedTaskIds.contains(task.getId())) {
                System.out.println("\n处理任务: " + task.getId());
                if (task.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                    // 只暂停正在下载的任务
                    downloader.pauseDownload(task);
                    hasTasksToPause = true;
                    System.out.println("任务已暂停: " + task.getId());
                } else {
                    System.out.println("任务状态为 " + task.getStatus() + ", 跳过暂停: " + task.getId());
                }
            }
        }
        
        // 检查任务状态变化
        System.out.println("\n暂停后状态:");
        System.out.println("task1状态: " + task1.getStatus());
        System.out.println("task2状态: " + task2.getStatus());
        System.out.println("task3状态: " + task3.getStatus());
        
        // 验证结果
        if (task1.getStatus() == DownloadTaskInfo.TaskStatus.WAITING) {
            System.out.println("\n✅ 测试通过: 正在下载的任务(test1)已成功暂停");
        } else {
            System.out.println("\n❌ 测试失败: 正在下载的任务(test1)未暂停");
        }
        
        if (task2.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
            System.out.println("✅ 测试通过: 未选择的任务(test2)仍在下载");
        } else {
            System.out.println("❌ 测试失败: 未选择的任务(test2)被意外暂停");
        }
        
        if (task3.getStatus() == DownloadTaskInfo.TaskStatus.WAITING) {
            System.out.println("✅ 测试通过: 等待中的任务(test3)保持不变");
        } else {
            System.out.println("❌ 测试失败: 等待中的任务(test3)状态被意外修改");
        }
        
        System.out.println("\n测试2: 未选择任何任务");
        selectedTaskIds.clear();
        
        hasTasksToPause = false;
        for (DownloadTaskInfo task : taskManager.getAllTasks()) {
            if (selectedTaskIds.contains(task.getId())) {
                if (task.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                    downloader.pauseDownload(task);
                    hasTasksToPause = true;
                }
            }
        }
        
        if (!hasTasksToPause) {
            System.out.println("✅ 测试通过: 未选择任务时，不会暂停任何任务");
        } else {
            System.out.println("❌ 测试失败: 未选择任务时，仍暂停了任务");
        }
        
        System.out.println("\n批量暂停功能测试完成!");
    }
}