package com.example.download.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.example.download.model.DownloadTaskInfo;

/**
 * 下载详情对话框，显示下载任务的详细信息和进度
 */
public class DownloadDetailDialog extends JDialog {
    private DownloadTaskInfo taskInfo;
    private JProgressBar totalProgressBar;
    private JTable chunkProgressTable;
    private DefaultTableModel chunkProgressModel;
    private JTextArea logTextArea;
    private Timer refreshTimer;
    private DecimalFormat df = new DecimalFormat("0.00");
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public DownloadDetailDialog(JFrame parent, DownloadTaskInfo taskInfo) {
        super(parent, "下载详情 - " + taskInfo.getFileName(), true);
        this.taskInfo = taskInfo;
        
        initializeUI();
        startRefreshTimer();
        
        // 设置窗口关闭事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopRefreshTimer();
            }
        });
    }

    private void initializeUI() {
        setSize(800, 600);
        setLocationRelativeTo(getParent());
        
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // 创建标签页
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 下载进度标签页
        JPanel progressTabPanel = new JPanel(new BorderLayout());
        progressTabPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // 任务基本信息
        JPanel infoPanel = new JPanel(new GridLayout(4, 2, 10, 5));
        infoPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        infoPanel.add(new JLabel("文件名:"));
        infoPanel.add(new JLabel(taskInfo.getFileName()));
        
        infoPanel.add(new JLabel("下载URL:"));
        JTextField urlTextField = new JTextField(taskInfo.getUrl());
        urlTextField.setEditable(false);
        infoPanel.add(urlTextField);
        
        infoPanel.add(new JLabel("保存路径:"));
        JTextField savePathTextField = new JTextField(taskInfo.getSavePath());
        savePathTextField.setEditable(false);
        infoPanel.add(savePathTextField);
        
        infoPanel.add(new JLabel("线程数:"));
        infoPanel.add(new JLabel(String.valueOf(taskInfo.getThreadCount())));
        
        progressTabPanel.add(infoPanel, BorderLayout.NORTH);
        
        // 总进度条
        JPanel totalProgressPanel = new JPanel(new BorderLayout());
        totalProgressPanel.setBorder(BorderFactory.createTitledBorder("总下载进度"));
        totalProgressPanel.setPreferredSize(new Dimension(0, 60));
        
        totalProgressBar = new JProgressBar(0, 100);
        totalProgressBar.setStringPainted(true);
        totalProgressBar.setString("0% - 等待下载");
        totalProgressPanel.add(totalProgressBar, BorderLayout.CENTER);
        
        JPanel progressInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField downloadedSizeField = new JTextField(15);
        downloadedSizeField.setEditable(false);
        JTextField speedField = new JTextField(15);
        speedField.setEditable(false);
        JTextField remainingTimeField = new JTextField(15);
        remainingTimeField.setEditable(false);
        
        progressInfoPanel.add(new JLabel("已下载:"));
        progressInfoPanel.add(downloadedSizeField);
        progressInfoPanel.add(new JLabel("速度:"));
        progressInfoPanel.add(speedField);
        progressInfoPanel.add(new JLabel("剩余时间:"));
        progressInfoPanel.add(remainingTimeField);
        
        totalProgressPanel.add(progressInfoPanel, BorderLayout.SOUTH);
        
        progressTabPanel.add(totalProgressPanel, BorderLayout.CENTER);
        
        // 分块下载进度表格
        JPanel chunkProgressPanel = new JPanel(new BorderLayout());
        chunkProgressPanel.setBorder(BorderFactory.createTitledBorder("分块下载进度"));
        
        String[] columnNames = {"分块ID", "开始字节", "结束字节", "大小", "状态", "下载进度"};
        chunkProgressModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        chunkProgressTable = new JTable(chunkProgressModel);
        chunkProgressTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        
        // 设置列宽
        chunkProgressTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        chunkProgressTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        chunkProgressTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        chunkProgressTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        chunkProgressTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        chunkProgressTable.getColumnModel().getColumn(5).setPreferredWidth(150);
        
        JScrollPane chunkProgressScrollPane = new JScrollPane(chunkProgressTable);
        chunkProgressPanel.add(chunkProgressScrollPane, BorderLayout.CENTER);
        
        progressTabPanel.add(chunkProgressPanel, BorderLayout.SOUTH);
        
        // 下载日志标签页
        JPanel logTabPanel = new JPanel(new BorderLayout());
        logTabPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        
        logTabPanel.add(logScrollPane, BorderLayout.CENTER);
        
        // 添加标签页
        tabbedPane.addTab("下载进度", progressTabPanel);
        tabbedPane.addTab("下载日志", logTabPanel);
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        setContentPane(mainPanel);
    }
    
    private void startRefreshTimer() {
        // 创建定时器，每秒刷新一次进度
        refreshTimer = new Timer(1000, e -> updateProgress());
        refreshTimer.start();
    }
    
    private void stopRefreshTimer() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            refreshTimer.stop();
        }
    }
    
    private void updateProgress() {
        if (taskInfo != null) {
            // 更新总进度
            int progress = taskInfo.getProgress();
            totalProgressBar.setValue(progress);
            totalProgressBar.setString(progress + "% - " + taskInfo.getStatus().toString());
            
            // 更新分块进度表格
            updateChunkProgressTable();
        }
    }
    
    private void updateChunkProgressTable() {
        // 这里需要根据实际的分块下载实现来更新表格
        // 暂时显示模拟数据
        chunkProgressModel.setRowCount(0);
        
        // 根据线程数模拟分块数据
        int threadCount = taskInfo.getThreadCount();
        long fileSize = taskInfo.getFileSize();
        long chunkSize = fileSize / threadCount;
        
        for (int i = 0; i < threadCount; i++) {
            String[] rowData = new String[6];
            rowData[0] = String.valueOf(i + 1);
            rowData[1] = String.valueOf(i * chunkSize);
            rowData[2] = String.valueOf((i + 1) * chunkSize - 1);
            rowData[3] = formatFileSize(chunkSize);
            
            // 根据任务状态设置分块状态
            if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                rowData[4] = "下载中";
                rowData[5] = taskInfo.getProgress() + "%";
            } else if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.COMPLETED) {
                rowData[4] = "已完成";
                rowData[5] = "100%";
            } else if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.CANCELED) {
                rowData[4] = "已取消";
                rowData[5] = taskInfo.getProgress() + "%";
            } else if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.FAILED) {
                rowData[4] = "下载失败";
                rowData[5] = taskInfo.getProgress() + "%";
            } else {
                rowData[4] = "等待中";
                rowData[5] = "0%";
            }
            
            chunkProgressModel.addRow(rowData);
        }
    }
    
    /**
     * 格式化文件大小
     * @param size 文件大小（字节）
     * @return 格式化后的文件大小字符串
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return df.format(size / 1024.0) + " KB";
        } else if (size < 1024 * 1024 * 1024) {
            return df.format(size / (1024.0 * 1024.0)) + " MB";
        } else {
            return df.format(size / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }
    
    /**
     * 格式化时间
     * @param date 日期对象
     * @return 格式化后的时间字符串
     */
    private String formatDateTime(Date date) {
        if (date == null) {
            return "-";
        }
        return sdf.format(date);
    }
}
