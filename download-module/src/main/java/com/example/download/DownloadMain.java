package com.example.download;

import com.example.download.core.MultiThreadDownloader;

public class DownloadMain {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("使用方法: java DownloadMain <文件URL> <保存路径> [线程数]");
            System.out.println("示例: java DownloadMain https://example.com/file.zip D:\\Downloads\\file.zip 4");
            return;
        }

        String fileUrl = args[0];
        String savePath = args[1];
        int threadCount = args.length > 2 ? Integer.parseInt(args[2]) : 4;

        MultiThreadDownloader downloader = new MultiThreadDownloader();

        try {
            System.out.println("开始下载文件: " + fileUrl);
            System.out.println("保存路径: " + savePath);
            System.out.println("线程数: " + threadCount);
            System.out.println("========================================");

            long startTime = System.currentTimeMillis();
            downloader.download(fileUrl, savePath, threadCount);
            long endTime = System.currentTimeMillis();

            System.out.println("========================================");
            System.out.println("下载完成，总耗时: " + (endTime - startTime) / 1000 + " 秒");
        } catch (Exception e) {
            System.err.println("下载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}