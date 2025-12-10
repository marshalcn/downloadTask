package com.example.download.core;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.download.model.DownloadTaskInfo;
import com.example.download.ui.DownloadGUI;

public class MultiThreadDownloader {
    private static final int DEFAULT_THREAD_COUNT = 4;
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024; // 默认1MB
    private int chunkSize; // 每个任务下载的大小

    // 进度监听器
    private DownloadGUI.ProgressListener progressListener;
    // 文件总大小
    private long totalFileSize;
    // 下载任务映射，用于管理正在下载的任务
    private Map<String, DownloadTaskContext> downloadTasks = new ConcurrentHashMap<>();

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
     * 开始下载任务
     *
     * @param taskInfo 任务信息对象
     */
    public void startDownload(DownloadTaskInfo taskInfo) {
        // 如果任务已经在下载中，直接返回
        if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
            return;
        }
        
        // 创建下载任务上下文
        DownloadTaskContext context = new DownloadTaskContext();
        downloadTasks.put(taskInfo.getId(), context);
        
        // 更新任务状态
        taskInfo.setStatus(DownloadTaskInfo.TaskStatus.DOWNLOADING);
        
        // 启动下载线程
        Thread downloadThread = new Thread(() -> {
            try {
                download(taskInfo, null, DEFAULT_CHUNK_SIZE);
            } catch (Exception e) {
                taskInfo.setStatus(DownloadTaskInfo.TaskStatus.FAILED);
                log("下载失败: " + e.getMessage());
            } finally {
                downloadTasks.remove(taskInfo.getId());
            }
        });
        
        downloadThread.start();
    }
    
    /**
     * 暂停下载任务
     *
     * @param taskInfo 任务信息对象
     */
    public void pauseDownload(DownloadTaskInfo taskInfo) {
        // 如果任务不在下载中，直接返回
        if (taskInfo.getStatus() != DownloadTaskInfo.TaskStatus.DOWNLOADING) {
            return;
        }
        
        // 获取任务上下文
        DownloadTaskContext context = downloadTasks.get(taskInfo.getId());
        if (context != null) {
            // 统计实际已下载大小（从文件块索引）
            try {
                // 构建临时目录和索引文件路径
                File tempDir = new File(taskInfo.getSavePath() + ".tmp" + taskInfo.getId());
                File indexFile = new File(tempDir, "index.dat");
                
                // 加载已完成的范围
                Set<DownloadRange> completedRanges = loadCompletedRanges(indexFile);
                
                // 计算实际已下载大小
                long actualDownloadedSize = calculateDownloadedSize(taskInfo.getSavePath(), completedRanges, taskInfo.getFileSize());
                
                // 更新任务信息
                taskInfo.setDownloadedSize(actualDownloadedSize);
                if (context.getDownloadedBytes() != null) {
                    context.getDownloadedBytes().set((int) actualDownloadedSize);
                }
                
                log("暂停时统计的实际已下载大小: " + actualDownloadedSize + " bytes");
            } catch (Exception e) {
                log("暂停时统计已下载大小失败: " + e.getMessage());
            }
        }
        
        // 更新任务状态
        taskInfo.setStatus(DownloadTaskInfo.TaskStatus.PAUSED);
        
        // 从任务映射中移除
        downloadTasks.remove(taskInfo.getId());
        
        log("下载任务已暂停: " + taskInfo.getFileName());
    }
    
    /**
     * 多线程下载文件 - 任务队列模式（带进度监听和任务信息）
     *
     * @param taskInfo 任务信息对象
     * @param listener 进度监听器
     * @throws Exception 下载异常
     */
    public void download(DownloadTaskInfo taskInfo, DownloadGUI.ProgressListener listener) throws Exception {
        download(taskInfo, listener, DEFAULT_CHUNK_SIZE);
    }

    public void download(DownloadTaskInfo taskInfo, DownloadGUI.ProgressListener listener, int chunkSize) throws Exception {
        this.chunkSize = chunkSize;
        if (taskInfo == null || taskInfo.getUrl() == null || taskInfo.getSavePath() == null) {
            throw new IllegalArgumentException("任务信息、文件URL和保存路径不能为空");
        }

        this.progressListener = listener;
        
        // 获取或创建任务上下文
        DownloadTaskContext context = downloadTasks.get(taskInfo.getId());
        if (context == null) {
            // 恢复下载时，创建新的任务上下文
            context = new DownloadTaskContext();
            downloadTasks.put(taskInfo.getId(), context);
        }
        
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
        File downloadFile = new File(savePath);
        try (RandomAccessFile raf = new RandomAccessFile(savePath, "rw")) {
            // 设置文件大小
            if (raf.length() < totalFileSize) {
                raf.setLength(totalFileSize);
            }
        }
        
        // 创建临时目录和索引文件
        String tempDirPath = saveDir.getAbsolutePath() + File.separator + ".temp-" + taskInfo.getId();
        File tempDir = new File(tempDirPath);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File indexFile = new File(tempDir, "index.txt");
        
        // 更新上下文信息
        context.setTempDir(tempDir);
        context.setIndexFile(indexFile);
        
        // 从索引文件加载已完成的任务
        Set<DownloadRange> completedRanges = loadCompletedRanges(indexFile);
        if (completedRanges == null) {
            completedRanges = new HashSet<>();
        }
        
        // 生成所有文件区块的索引
        Set<DownloadRange> allRanges = generateAllRanges(totalFileSize);
        
        // 合并已完成的范围状态
        for (DownloadRange completedRange : completedRanges) {
            for (DownloadRange range : allRanges) {
                if (range.equals(completedRange)) {
                    range.setStatus(DownloadRange.Status.DOWNLOADED);
                    break;
                }
            }
        }
        
        // 保存所有区块的索引（包括未下载的）到索引文件
        saveAllRanges(indexFile, allRanges);
        
        // 更新上下文和任务信息
        context.getCompletedRanges().addAll(completedRanges);
        log("从索引文件加载已完成任务数: " + completedRanges.size());
        
        // 计算已下载的大小
        long downloadedSize = calculateDownloadedSize(savePath, completedRanges, taskInfo.getFileSize());
        taskInfo.setDownloadedSize(downloadedSize);
        context.getDownloadedBytes().set((int) downloadedSize);
        
        // 创建任务队列
        BlockingQueue<DownloadRange> taskQueue = new LinkedBlockingQueue<>();
        int taskCount = generateDownloadTasks(taskQueue, allRanges);
        log("生成下载任务数: " + taskCount);
        
        // 保存总任务数到上下文
        context.setTotalTasks(taskCount);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(taskCount);

        // 提交下载任务
        for (int i = 0; i < threadCount; i++) {
            executor.submit(new DownloadTask(fileUrl, savePath, taskQueue, latch, taskInfo, tempDir, indexFile, context));
        }

        // 计算下载速度和剩余时间的线程
        DownloadSpeedCalculator speedCalculator = new DownloadSpeedCalculator(taskInfo, context);
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

        // 检查下载是否真的完成
        long finalDownloadedSize = context.getDownloadedBytes().get();
        if (finalDownloadedSize == totalFileSize) {
            // 更新任务状态为已完成
            taskInfo.setStatus(DownloadTaskInfo.TaskStatus.COMPLETED);
            taskInfo.setDownloadedSize(totalFileSize);
            log("文件下载完成: " + savePath);
            
            // 删除临时目录
            deleteTempDir(tempDir);
        } else {
            // 如果下载未完成且任务状态仍然是DOWNLOADING，设置为WAITING
            if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                taskInfo.setStatus(DownloadTaskInfo.TaskStatus.WAITING);
            }
            // 更新任务的已下载大小
            taskInfo.setDownloadedSize(finalDownloadedSize);
            log("文件下载暂停或部分完成，已下载: " + finalDownloadedSize + " bytes");
            log("剩余下载区块数量: " + context.getRemainingTasks());
        }
    }
    
    /**
     * 下载任务上下文类，用于管理下载任务的状态
     */
    private class DownloadTaskContext {
        private Set<DownloadRange> completedRanges = new HashSet<>();
        private AtomicInteger downloadedBytes = new AtomicInteger(0);
        private File tempDir;
        private File indexFile;
        private BlockingQueue<DownloadRange> pendingTasks;
        private int totalTasks;
        private int completedTasks;
        
        public DownloadTaskContext() {
            this.pendingTasks = new LinkedBlockingQueue<>();
            this.completedTasks = 0;
        }
        
        public File getTempDir() {
            return tempDir;
        }
        
        public void setTempDir(File tempDir) {
            this.tempDir = tempDir;
        }
        
        public File getIndexFile() {
            return indexFile;
        }
        
        public void setIndexFile(File indexFile) {
            this.indexFile = indexFile;
        }
        
        public BlockingQueue<DownloadRange> getPendingTasks() {
            return pendingTasks;
        }
        
        public int getTotalTasks() {
            return totalTasks;
        }
        
        public void setTotalTasks(int totalTasks) {
            this.totalTasks = totalTasks;
        }
        
        public int getCompletedTasks() {
            return completedTasks;
        }
        
        public int getRemainingTasks() {
            return totalTasks - completedTasks;
        }
        
        public synchronized void incrementCompletedTasks() {
            completedTasks++;
        }
        
        public Set<DownloadRange> getCompletedRanges() {
            return completedRanges;
        }
        
        public void addCompletedRange(DownloadRange range) {
            // 设置下载状态为已下载
            range.setStatus(DownloadRange.Status.DOWNLOADED);
            completedRanges.add(range);
        }
        
        public boolean isRangeCompleted(DownloadRange range) {
            return completedRanges.contains(range);
        }
        
        public AtomicInteger getDownloadedBytes() {
            return downloadedBytes;
        }
        
        public void setDownloadedBytes(int value) {
            downloadedBytes.set(value);
        }
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
        
        download(taskInfo, listener, DEFAULT_CHUNK_SIZE);
    }

    /**
     * 生成下载任务队列（乱序）
     *
     * @param taskQueue 任务队列
     * @param fileSize  文件大小
     * @return 任务总数
     */
    /**
     * 生成所有文件区块的索引
     * @param fileSize 文件大小
     * @return 所有文件区块的集合
     */
    private Set<DownloadRange> generateAllRanges(long fileSize) {
        Set<DownloadRange> allRanges = new HashSet<>();
        long start = 0;
        long chunkSize;
        
        // 根据文件大小确定分块规则
        if (fileSize < 10 * 1024 * 1024) { // < 10MB
            // 小于10MB时默认分为10块
            long blockSize = fileSize / 10;
            if (blockSize == 0) blockSize = fileSize;
            
            for (int i = 0; i < 10; i++) {
                long end = Math.min(start + blockSize - 1, fileSize - 1);
                // 创建区块并设置初始状态为未下载
                DownloadRange range = new DownloadRange(start, end, DownloadRange.Status.NOT_DOWNLOADED);
                allRanges.add(range);
                
                start = end + 1;
                if (start >= fileSize) break;
            }
        } else {
            // 超过10MB的每个文件块大小为1Mb
            chunkSize = 1024 * 1024;
            
            // 生成所有下载任务
            while (start < fileSize) {
                long end = Math.min(start + chunkSize - 1, fileSize - 1);
                // 创建区块并设置初始状态为未下载
                DownloadRange range = new DownloadRange(start, end, DownloadRange.Status.NOT_DOWNLOADED);
                allRanges.add(range);
                
                start = end + 1;
            }
        }
        
        return allRanges;
    }
    
    /**
     * 生成下载任务队列（只包含未完成的任务）
     * @param taskQueue 任务队列
     * @param allRanges 所有文件区块
     * @return 任务总数
     */
    private int generateDownloadTasks(BlockingQueue<DownloadRange> taskQueue, Set<DownloadRange> allRanges) {
        List<DownloadRange> tasks = new ArrayList<>();
        
        // 只添加未完成的任务
        for (DownloadRange range : allRanges) {
            if (!range.isDownloaded()) {
                tasks.add(range);
            }
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
     * 保存所有文件区块的索引到文件
     * @param indexFile 索引文件
     * @param allRanges 所有文件区块
     */
    private void saveAllRanges(File indexFile, Set<DownloadRange> allRanges) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(indexFile))) {
            for (DownloadRange range : allRanges) {
                // 格式：startByte-endByte-status
                // status: 1=已下载, 0=未下载
                int statusValue = range.isDownloaded() ? 1 : 0;
                writer.write(range.getStartByte() + "-" + range.getEndByte() + "-" + statusValue);
                writer.newLine();
            }
        }
    }
    
    /**
     * 从索引文件加载已完成的下载范围
     */
    private Set<DownloadRange> loadCompletedRanges(File indexFile) throws Exception {
        Set<DownloadRange> completedRanges = new HashSet<>();
        
        if (indexFile.exists() && indexFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(indexFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    
                    // 格式：startByte-endByte-status
                    String[] parts = line.split("-");
                    if (parts.length == 3) {
                        try {
                            long start = Long.parseLong(parts[0]);
                            long end = Long.parseLong(parts[1]);
                            String statusStr = parts[2];
                            DownloadRange.Status status = statusStr.equals("1") ? DownloadRange.Status.DOWNLOADED : DownloadRange.Status.NOT_DOWNLOADED;
                            DownloadRange range = new DownloadRange(start, end, status);
                            completedRanges.add(range);
                        } catch (NumberFormatException e) {
                            log("解析索引文件行失败: " + line);
                        }
                    } else if (parts.length == 2) {
                        // 兼容旧格式：startByte-endByte
                        try {
                            long start = Long.parseLong(parts[0]);
                            long end = Long.parseLong(parts[1]);
                            completedRanges.add(new DownloadRange(start, end, DownloadRange.Status.DOWNLOADED));
                        } catch (NumberFormatException e) {
                            log("解析索引文件行失败: " + line);
                        }
                    }
                }
            }
        }
        
        return completedRanges;
    }
    
    /**
     * 保存已完成的下载范围到索引文件
     */
    private void saveCompletedRanges(File indexFile, Set<DownloadRange> completedRanges) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(indexFile))) {
            for (DownloadRange range : completedRanges) {
                // 格式：startByte-endByte-status
                // status: 1=已下载, 0=未下载
                int statusValue = range.isDownloaded() ? 1 : 0;
                writer.write(range.getStartByte() + "-" + range.getEndByte() + "-" + statusValue);
                writer.newLine();
            }
        }
    }
    
    /**
     * 计算已下载的文件大小
     */
    private long calculateDownloadedSize(String savePath, Set<DownloadRange> completedRanges, long fileSize) {
        // 计算已完成范围的总大小
        long totalRangeSize = 0;
        for (DownloadRange range : completedRanges) {
            totalRangeSize += range.getEndByte() - range.getStartByte() + 1;
        }
        
        // 检查实际文件大小
        File downloadFile = new File(savePath);
        if (downloadFile.exists()) {
            long actualSize = downloadFile.length();
            // 返回较小的值，确保不会超过文件总大小
            return Math.min(Math.min(totalRangeSize, actualSize), fileSize);
        }
        
        return Math.min(totalRangeSize, fileSize);
    }
    
    /**
     * 获取剩余下载区块数量
     */
    public int getRemainingTaskCount(String taskId) {
        DownloadTaskContext context = downloadTasks.get(taskId);
        if (context != null) {
            return context.getRemainingTasks();
        }
        return 0;
    }
    
    /**
     * 从索引文件中获取已下载的大小
     * 
     * @param taskInfo 任务信息对象
     * @return 已下载的大小（字节），如果获取失败则返回当前任务信息中的下载大小
     */
    public long getDownloadedSizeFromIndex(DownloadTaskInfo taskInfo) {
        try {
            // 构建临时目录和索引文件路径
            File tempDir = new File(taskInfo.getSavePath() + ".tmp" + taskInfo.getId());
            File indexFile = new File(tempDir, "index.dat");
            
            // 加载已完成的范围
            Set<DownloadRange> completedRanges = loadCompletedRanges(indexFile);
            
            // 计算实际已下载大小
            return calculateDownloadedSize(taskInfo.getSavePath(), completedRanges, taskInfo.getFileSize());
        } catch (Exception e) {
            log("从索引文件获取已下载大小失败: " + e.getMessage());
            // 如果获取失败，返回当前任务信息中的下载大小
            return taskInfo.getDownloadedSize();
        }
    }
    
    /**
     * 删除临时目录
     */
    /**
     * 删除临时目录
     * @param tempDir 临时目录
     */
    private void deleteTempDir(File tempDir) {
        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            tempDir.delete();
        }
    }
    
    /**
     * 清理指定任务的临时文件和目录
     * @param savePath 保存路径
     * @param taskId 任务ID
     */
    public void cleanupTaskTempFiles(String savePath, String taskId) {
        // 临时目录位于savePath下，名称为taskId
        File tempDir = new File(savePath, taskId);
        deleteTempDir(tempDir);
        
        // 同时删除索引文件
        File indexFile = new File(savePath, taskId + ".idx");
        if (indexFile.exists()) {
            indexFile.delete();
        }
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
        private final DownloadTaskContext context;
        private volatile boolean running = true;
        private long lastDownloadedBytes = 0;

        public DownloadSpeedCalculator(DownloadTaskInfo taskInfo, DownloadTaskContext context) {
            this.taskInfo = taskInfo;
            this.context = context;
        }
        
        @Override
        public void run() {
            while (running) {
                try {
                    // 每1秒计算一次
                    Thread.sleep(1000);
                    
                    long currentDownloaded = context.getDownloadedBytes().get();
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
        private final File tempDir;
        private final File indexFile;
        private final DownloadTaskContext context;

        public DownloadTask(String fileUrl, String savePath, BlockingQueue<DownloadRange> taskQueue, CountDownLatch latch, DownloadTaskInfo taskInfo, File tempDir, File indexFile, DownloadTaskContext context) {
            this.fileUrl = fileUrl;
            this.savePath = savePath;
            this.taskQueue = taskQueue;
            this.latch = latch;
            this.taskInfo = taskInfo;
            this.tempDir = tempDir;
            this.indexFile = indexFile;
            this.context = context;
        }

        @Override
        public void run() {
            DownloadRange range;
            while ((range = taskQueue.poll()) != null) {
                // 检查任务状态，如果不是下载中，立即停止
                if (taskInfo.getStatus() != DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                    log("线程 " + Thread.currentThread().getName() + " 检测到任务已暂停，停止下载");
                    break;
                }
                
                // 检查该范围是否已经下载完成
                DownloadTaskContext context = downloadTasks.get(taskInfo.getId());
                if (context != null && context.isRangeCompleted(range)) {
                    log("线程 " + Thread.currentThread().getName() + " 跳过已完成的范围: " + range.getStartByte() + "-" + range.getEndByte());
                    latch.countDown();
                    continue;
                }
                
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
                            // 检查任务状态，如果不是下载中，立即停止
                            if (taskInfo.getStatus() != DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                                log("线程 " + Thread.currentThread().getName() + " 检测到任务已暂停，停止当前下载块");
                                // 暂停时忽略当前线程的内容，不保存到文件
                                break;
                            }
                            
                            raf.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                            
                            // 获取当前任务的上下文
                            if (context != null) {
                                // 更新已下载字节数
                                context.getDownloadedBytes().addAndGet(bytesRead);
                                
                                // 更新任务的已下载大小
                                long currentDownloaded = context.getDownloadedBytes().get();
                                taskInfo.setDownloadedSize(currentDownloaded);
                                
                                // 通知进度更新
                                if (progressListener != null) {
                                    progressListener.onProgress(currentDownloaded, totalFileSize);
                                }
                            }
                        }

                        if (totalRead == taskSize && taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                            log("线程 " + Thread.currentThread().getName() + " 完成任务: " + startByte + "-" + endByte);
                            // 记录已完成的范围
                            if (context != null) {
                                context.addCompletedRange(range);
                                // 保存已完成的范围到索引文件
                                saveCompletedRanges(indexFile, context.getCompletedRanges());
                                // 更新完成的任务数
                                context.incrementCompletedTasks();
                            }
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
        private Status status;

        // 下载状态枚举
        public enum Status {
            DOWNLOADED, NOT_DOWNLOADED
        }

        public DownloadRange(long startByte, long endByte) {
            this(startByte, endByte, Status.NOT_DOWNLOADED);
        }

        public DownloadRange(long startByte, long endByte, Status status) {
            this.startByte = startByte;
            this.endByte = endByte;
            this.status = status;
        }

        public long getStartByte() {
            return startByte;
        }
        
        public long getEndByte() {
            return endByte;
        }
        
        public Status getStatus() {
            return status;
        }
        
        public void setStatus(Status status) {
            this.status = status;
        }
        
        // 判断是否已下载
        public boolean isDownloaded() {
            return status == Status.DOWNLOADED;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DownloadRange that = (DownloadRange) o;
            return startByte == that.startByte && endByte == that.endByte;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(startByte, endByte);
        }
        
        @Override
        public String toString() {
            return "DownloadRange{" +
                    "startByte=" + startByte +
                    ", endByte=" + endByte +
                    ", status=" + status +
                    '}';
        }
    }
}