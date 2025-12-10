package com.example.download.manager;

import java.io.*;
import java.util.Properties;

/**
 * 配置管理类，用于读取和写入配置文件
 */
public class ConfigManager {
    private static final String CONFIG_FILE_NAME = "download_config.properties";
    private static final String DEFAULT_DOWNLOAD_PATH_KEY = "default_download_path";
    private static final String DEFAULT_THREAD_COUNT_KEY = "default_thread_count";
    
    private Properties properties;
    private File configFile;
    
    /**
     * 构造函数，初始化配置文件
     */
    public ConfigManager() {
        properties = new Properties();
        
        // 获取用户主目录
        String userHome = System.getProperty("user.home");
        configFile = new File(userHome, CONFIG_FILE_NAME);
        
        // 如果配置文件存在，则加载
        if (configFile.exists()) {
            loadConfig();
        } else {
            // 如果配置文件不存在，则使用默认值
            setDefaultDownloadPath(System.getProperty("user.home"));
            setDefaultThreadCount(4);
            saveConfig();
        }
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        try (InputStream input = new FileInputStream(configFile)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("加载配置文件失败: " + e.getMessage());
            // 加载失败时使用默认值
            setDefaultDownloadPath(System.getProperty("user.home"));
            setDefaultThreadCount(4);
        }
    }
    
    /**
     * 保存配置文件
     */
    private void saveConfig() {
        try (OutputStream output = new FileOutputStream(configFile)) {
            properties.store(output, "Download Tool Configuration");
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取默认下载路径
     * 
     * @return 默认下载路径
     */
    public String getDefaultDownloadPath() {
        return properties.getProperty(DEFAULT_DOWNLOAD_PATH_KEY, System.getProperty("user.home"));
    }
    
    /**
     * 设置默认下载路径
     * 
     * @param path 默认下载路径
     */
    public void setDefaultDownloadPath(String path) {
        properties.setProperty(DEFAULT_DOWNLOAD_PATH_KEY, path);
        saveConfig();
    }
    
    /**
     * 获取默认线程数
     * 
     * @return 默认线程数
     */
    public int getDefaultThreadCount() {
        String threadCountStr = properties.getProperty(DEFAULT_THREAD_COUNT_KEY, "4");
        try {
            return Integer.parseInt(threadCountStr);
        } catch (NumberFormatException e) {
            // 解析失败时使用默认值4
            setDefaultThreadCount(4);
            return 4;
        }
    }
    
    /**
     * 设置默认线程数
     * 
     * @param threadCount 默认线程数
     */
    public void setDefaultThreadCount(int threadCount) {
        properties.setProperty(DEFAULT_THREAD_COUNT_KEY, String.valueOf(threadCount));
        saveConfig();
    }
    
    /**
     * 获取配置文件路径
     * 
     * @return 配置文件路径
     */
    public String getConfigFilePath() {
        return configFile.getAbsolutePath();
    }
}