package com.example.download.model;

import java.io.Serializable;
import java.util.Date;

/**
 * 下载任务信息类，用于记录下载任务的详细信息
 */
public class DownloadTaskInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum TaskStatus {
        WAITING,    // 等待中
        DOWNLOADING, // 下载中
        PAUSED,     // 已暂停
        COMPLETED,  // 已完成
        CANCELED,   // 已取消
        FAILED      // 失败
    }
    
    private String id;                // 任务ID
    private String url;               // 下载URL
    private String savePath;          // 保存路径
    private String fileName;          // 文件名
    private int threadCount;          // 线程数
    private TaskStatus status;        // 任务状态
    private Date addTime;             // 添加时间
    private Date completedTime;       // 完成时间
    private long fileSize;            // 文件大小
    private long downloadedSize;      // 已下载大小
    private double downloadSpeed;     // 下载速度（KB/s）
    
    public DownloadTaskInfo() {
        this.id = generateId();
        this.addTime = new Date();
        this.status = TaskStatus.WAITING;
    }
    
    /**
     * 生成任务ID
     * 
     * @return 任务ID
     */
    private String generateId() {
        return "task_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getSavePath() {
        return savePath;
    }
    
    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public int getThreadCount() {
        return threadCount;
    }
    
    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }
    
    public TaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(TaskStatus status) {
        this.status = status;
        if (status == TaskStatus.COMPLETED) {
            this.completedTime = new Date();
        }
    }
    
    public Date getAddTime() {
        return addTime;
    }
    
    public Date getCompletedTime() {
        return completedTime;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public long getDownloadedSize() {
        return downloadedSize;
    }
    
    public void setDownloadedSize(long downloadedSize) {
        this.downloadedSize = downloadedSize;
    }
    
    public double getDownloadSpeed() {
        return downloadSpeed;
    }
    
    public void setDownloadSpeed(double downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }
    
    /**
     * 获取下载进度百分比
     * 
     * @return 下载进度百分比（0-100）
     */
    public int getProgress() {
        if (fileSize <= 0) {
            return 0;
        }
        return (int) ((downloadedSize * 100) / fileSize);
    }
    
    /**
     * 获取预计剩余时间（秒）
     * 
     * @return 预计剩余时间，如果无法计算则返回-1
     */
    public long getEstimatedTimeRemaining() {
        if (status != TaskStatus.DOWNLOADING || downloadSpeed <= 0) {
            return -1;
        }
        
        long remainingSize = fileSize - downloadedSize;
        if (remainingSize <= 0) {
            return 0;
        }
        
        // 转换为秒
        return (long) (remainingSize / (downloadSpeed * 1024));
    }
    
    /**
     * 格式化时间为字符串
     * 
     * @param time 时间
     * @return 格式化后的时间字符串
     */
    private String formatTime(Date time) {
        if (time == null) {
            return "-";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(time);
    }
    
    @Override
    public String toString() {
        return "DownloadTaskInfo{" +
                "id='" + id + '\'' +
                ", fileName='" + fileName + '\'' +
                ", status=" + status +
                ", progress=" + getProgress() + "%" +
                '}';
    }
}