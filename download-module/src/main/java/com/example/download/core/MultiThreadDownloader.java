package com.example.download.core;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.download.model.DownloadTaskInfo;
import com.example.download.ui.DownloadGUI;

public class MultiThreadDownloader {
    private static final int DEFAULT_THREAD_COUNT = 4;
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final int TASK_SIZE = 1024 * 1024; // 每个任务下载1MB大小

    // 进度监听器
    private DownloadGUI.ProgressListener progressListener;
    // 已下载字节数
    private AtomicInteger downloadedBytes = new AtomicInteger(0);
    // 文件总大小
    private long totalFileSize;

    /**
     * 多线程下载文件 - 任务队列模式
     *
     * @param fileUrl     文件URL
     * @param savePath    保存路径
     * @param threadCount 线程数量
     * @throws Exception  下载异常
     */
    public void download(String fileUrl, String savePath, int threadCount) throws Exception {
        download(fileUrl, savePath, threadCount, null);
    }

    /**
     * 多线程下载文件 - 任务队列模式（带进度监听和任务信息）
     *
     * @param taskInfo 任务信息对象
     * @param listener 进度监听器
     * @throws Exception 下载异常
     */
    public void download(DownloadTaskInfo taskInfo, DownloadGUI.ProgressListener listener) throws Exception {
        if (taskInfo == null || taskInfo.getUrl() == null || taskInfo.getSavePath() == null) {
            throw new IllegalArgumentException("任务信息、文件URL和保存路径不能为空");
        }

        this.progressListener = listener;
        this.downloadedBytes.set(0);
        
        String fileUrl = taskInfo.getUrl();
        String savePath = taskInfo.getSavePath();
        int threadCount = taskInfo.getThreadCount();
        
        if (threadCount <= 0) {
            threadCount = DEFAULT_THREAD_COUNT;
        }

        // 从URL提取文件名
        String fileName = extractFileName(fileUrl);
        
        // 检查保存路径是否是目录
        File saveLocation = new File(savePath);
        if (saveLocation.isDirectory()) {
            savePath = new File(saveLocation, fileName).getAbsolutePath();
        } else if (!saveLocation.getName().contains(".")) {
            // 如果保存路径没有扩展名，则认为是目录
            savePath = new File(saveLocation, fileName).getAbsolutePath();
        }

        // 更新任务信息
        taskInfo.setFileName(fileName);
        taskInfo.setStatus(DownloadTaskInfo.TaskStatus.DOWNLOADING);
        
        log("开始下载文件: " + fileUrl);
        log("保存路径: " + savePath);
        log("线程数: " + threadCount);
        log("========================================");

        // 获取文件大小
        this.totalFileSize = getFileSize(fileUrl);
        taskInfo.setFileSize(totalFileSize);
        log("文件大小: " + totalFileSize + " bytes");

        // 创建保存目录
        File saveDir = new File(savePath).getParentFile();
        if (saveDir != null && !saveDir.exists()) {
            saveDir.mkdirs();
        }

        // 创建空文件并设置大小
        try (RandomAccessFile raf = new RandomAccessFile(savePath, "rw")) {
            raf.setLength(totalFileSize);
        }

        // 创建任务队列
        BlockingQueue<DownloadRange> taskQueue = new LinkedBlockingQueue<>();
        int taskCount = generateDownloadTasks(taskQueue, totalFileSize);
        log("生成下载任务数: " + taskCount);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(taskCount);

        // 提交下载任务
        for (int i = 0; i < threadCount; i++) {
            executor.submit(new DownloadTask(fileUrl, savePath, taskQueue, latch, taskInfo));
        }

        // 计算下载速度和剩余时间的线程
        DownloadSpeedCalculator speedCalculator = new DownloadSpeedCalculator(taskInfo);
        Thread speedThread = new Thread(speedCalculator);
        speedThread.setDaemon(true);
        speedThread.start();

        // 等待所有线程完成
        latch.await();

        // 停止速度计算
        speedCalculator.stop();
        speedThread.join();

        // 关闭线程池
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // 更新任务状态为已完成
        taskInfo.setStatus(DownloadTaskInfo.TaskStatus.COMPLETED);
        taskInfo.setDownloadedSize(totalFileSize);
        
        log("文件下载完成: " + savePath);
    }
    
    /**
     * 多线程下载文件 - 任务队列模式（带进度监听）
     *
     * @param fileUrl     文件URL
     * @param savePath    保存路径
     * @param threadCount 线程数量
     * @param listener    进度监听器
     * @throws Exception  下载异常
     */
    public void download(String fileUrl, String savePath, int threadCount, DownloadGUI.ProgressListener listener) throws Exception {
        DownloadTaskInfo taskInfo = new DownloadTaskInfo();
        taskInfo.setUrl(fileUrl);
        taskInfo.setSavePath(savePath);
        taskInfo.setThreadCount(threadCount);
        
        download(taskInfo, listener);
    }

    /**
     * 生成下载任务队列（乱序）
     *
     * @param taskQueue 任务队列
     * @param fileSize  文件大小
     * @return 任务总数
     */
    private int generateDownloadTasks(BlockingQueue<DownloadRange> taskQueue, long fileSize) {
        List<DownloadRange> tasks = new ArrayList<>();
        long start = 0;

        // 生成所有下载任务
        while (start < fileSize) {
            long end = Math.min(start + TASK_SIZE - 1, fileSize - 1);
            tasks.add(new DownloadRange(start, end));
            start = end + 1;
        }

        // 随机打乱任务顺序
        Collections.shuffle(tasks);

        // 将乱序后的任务放入队列
        for (DownloadRange task : tasks) {
            taskQueue.offer(task);
        }

        return tasks.size();
    }

    /**
     * 获取文件大小
     *
     * @param fileUrl 文件URL
     * @return 文件大小
     * @throws Exception 异常
     */
    private long getFileSize(String fileUrl) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) openConnectionWithProxy(url);
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            return conn.getContentLengthLong();
        } else {
            throw new IOException("无法获取文件大小，HTTP状态码: " + conn.getResponseCode());
        }
    }
    
    /**
     * 使用系统代理打开URL连接
     *
     * @param url URL对象
     * @return HttpURLConnection连接
     * @throws IOException 异常
     */
    private HttpURLConnection openConnectionWithProxy(URL url) throws IOException {
        // 获取系统默认代理选择器
        java.net.ProxySelector proxySelector = java.net.ProxySelector.getDefault();
        if (proxySelector != null) {
            try {
                // 将URL转换为URI
                java.net.URI uri = url.toURI();
                // 获取该URI对应的代理列表
                java.util.List<java.net.Proxy> proxies = proxySelector.select(uri);
                if (proxies != null && !proxies.isEmpty()) {
                    // 使用第一个代理
                    java.net.Proxy proxy = proxies.get(0);
                    if (proxy.type() != java.net.Proxy.Type.DIRECT) {
                        log("使用系统代理: " + proxy);
                        return (HttpURLConnection) url.openConnection(proxy);
                    }
                }
            } catch (java.net.URISyntaxException e) {
                log("URL转URI失败，使用直接连接: " + e.getMessage());
            }
        }
        // 没有找到合适的代理，直接连接
        log("未使用系统代理，直接连接");
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * 记录日志
     *
     * @param message 日志消息
     */
    private void log(String message) {
        System.out.println(message);
        if (progressListener != null) {
            progressListener.onLog(message);
        }
    }

    /**
     * 从URL中提取文件名
     *
     * @param urlStr URL字符串
     * @return 文件名
     */
    private String extractFileName(String urlStr) {
        // 首先尝试从response-content-disposition参数中提取文件名
        try {
            // 查找response-content-disposition参数
            int pos = urlStr.indexOf("response-content-disposition=");
            if (pos != -1) {
                // 提取参数值
                String paramValue = urlStr.substring(pos + "response-content-disposition=".length());
                // 查找下一个参数的开始位置
                int endPos = paramValue.indexOf("&");
                if (endPos != -1) {
                    paramValue = paramValue.substring(0, endPos);
                }
                
                // 解析filename
                pos = paramValue.indexOf("filename=");
                if (pos != -1) {
                    String fileName = paramValue.substring(pos + "filename=".length());
                    // 移除可能的引号
                    if (fileName.startsWith("'") && fileName.endsWith("'")) {
                        fileName = fileName.substring(1, fileName.length() - 1);
                    } else if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
                        fileName = fileName.substring(1, fileName.length() - 1);
                    }
                    // URL解码
                    fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
                    return fileName;
                }
            }
        } catch (Exception e) {
            // 如果解析失败，继续尝试其他方法
        }
        
        // 尝试从URL路径中提取文件名
        try {
            java.net.URL url = new java.net.URL(urlStr);
            String path = url.getPath();
            if (path != null && !path.isEmpty()) {
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                // URL解码
                fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
                return fileName;
            }
        } catch (Exception e) {
            // 如果解析失败，使用默认文件名
        }
        
        // 如果以上方法都失败，使用默认文件名
        return "download_" + System.currentTimeMillis() + ".bin";
    }

    /**
     * 下载速度计算类，用于实时计算下载速度和预计剩余时间
     */
    private class DownloadSpeedCalculator implements Runnable {
        private final DownloadTaskInfo taskInfo;
        private volatile boolean running = true;
        private long lastDownloadedBytes = 0;
        
        public DownloadSpeedCalculator(DownloadTaskInfo taskInfo) {
            this.taskInfo = taskInfo;
        }
        
        @Override
        public void run() {
            while (running) {
                try {
                    // 每1秒计算一次
                    Thread.sleep(1000);
                    
                    long currentDownloaded = downloadedBytes.get();
                    long bytesDownloadedInSecond = currentDownloaded - lastDownloadedBytes;
                    
                    // 计算速度（KB/s）
                    double speed = bytesDownloadedInSecond / 1024.0;
                    taskInfo.setDownloadSpeed(speed);
                    
                    // 更新已下载大小
                    taskInfo.setDownloadedSize(currentDownloaded);
                    
                    lastDownloadedBytes = currentDownloaded;
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        public void stop() {
            running = false;
        }
    }
    
    /**
     * 下载任务类
     */
    private class DownloadTask implements Runnable {
        private final String fileUrl;
        private final String savePath;
        private final BlockingQueue<DownloadRange> taskQueue;
        private final CountDownLatch latch;
        private final DownloadTaskInfo taskInfo;

        public DownloadTask(String fileUrl, String savePath, BlockingQueue<DownloadRange> taskQueue, CountDownLatch latch, DownloadTaskInfo taskInfo) {
            this.fileUrl = fileUrl;
            this.savePath = savePath;
            this.taskQueue = taskQueue;
            this.latch = latch;
            this.taskInfo = taskInfo;
        }

        @Override
        public void run() {
            DownloadRange range;
            while ((range = taskQueue.poll()) != null) {
                try {
                    long startByte = range.getStartByte();
                    long endByte = range.getEndByte();
                    long taskSize = endByte - startByte + 1;
                    
                    log("线程 " + Thread.currentThread().getName() + " 开始下载: " + startByte + "-" + endByte);

                    URL url = new URL(fileUrl);
                    HttpURLConnection conn = (HttpURLConnection) openConnectionWithProxy(url);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    try (InputStream inputStream = conn.getInputStream();
                         RandomAccessFile raf = new RandomAccessFile(savePath, "rw")) {

                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long totalRead = 0;
                        
                        raf.seek(startByte);

                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            raf.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                            
                            // 更新已下载字节数
                            downloadedBytes.addAndGet(bytesRead);
                            
                            // 通知进度更新
                            if (progressListener != null) {
                                progressListener.onProgress(downloadedBytes.get(), totalFileSize);
                            }
                        }

                        if (totalRead == taskSize) {
                            log("线程 " + Thread.currentThread().getName() + " 完成任务: " + startByte + "-" + endByte);
                        } else {
                            log("线程 " + Thread.currentThread().getName() + " 任务下载不完整: " + startByte + "-" + endByte);
                        }
                    } finally {
                        conn.disconnect();
                        latch.countDown();
                    }
                } catch (Exception e) {
                    log("线程 " + Thread.currentThread().getName() + " 下载失败: " + e.getMessage());
                    latch.countDown();
                }
            }
        }
    }

    /**
     * 下载范围类
     */
    private static class DownloadRange {
        private final long startByte;
        private final long endByte;

        public DownloadRange(long startByte, long endByte) {
            this.startByte = startByte;
            this.endByte = endByte;
        }

        public long getStartByte() {
            return startByte;
        }

        public long getEndByte() {
            return endByte;
        }
    }
}