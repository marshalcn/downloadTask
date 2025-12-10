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
    // 下载进度信息显示文本框
    private JTextField downloadedSizeField;
    private JTextField speedField;
    private JTextField remainingTimeField;
    // 保存平均下载速度，用于下载完成后显示
    private double averageDownloadSpeed = 0.0;

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
        setSize(800, 700); // 增加窗口高度，确保所有内容都能显示
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
        infoPanel.setPreferredSize(new Dimension(0, 150)); // 设置合适的高度，确保所有信息项都能显示
        
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
        
        // 使用GridLayout实现三个信息区域的平均分布
        JPanel progressInfoPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        progressInfoPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
        
        // 已下载信息区域
        JPanel downloadedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        downloadedSizeField = new JTextField(25); // 再次增加文本框长度
        downloadedSizeField.setEditable(false);
        downloadedPanel.add(new JLabel("已下载:"));
        downloadedPanel.add(downloadedSizeField);
        
        // 速度信息区域
        JPanel speedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        speedField = new JTextField(15);
        speedField.setEditable(false);
        speedPanel.add(new JLabel("速度:"));
        speedPanel.add(speedField);
        
        // 剩余时间信息区域
        JPanel remainingTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        remainingTimeField = new JTextField(15);
        remainingTimeField.setEditable(false);
        remainingTimePanel.add(new JLabel("剩余时间:"));
        remainingTimePanel.add(remainingTimeField);
        
        // 将三个信息区域添加到主进度信息面板
        progressInfoPanel.add(downloadedPanel);
        progressInfoPanel.add(speedPanel);
        progressInfoPanel.add(remainingTimePanel);
        
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
        // 设置自动调整列宽模式，让表格内容铺满整个宽度
        chunkProgressTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        
        // 设置网格线可见
        chunkProgressTable.setShowGrid(true);
        chunkProgressTable.setGridColor(Color.GRAY);
        
        // 确保表头与表体之间有分隔线
        chunkProgressTable.getTableHeader().setBorder(BorderFactory.createEtchedBorder());
        
        // 设置列宽，表格会根据需要自动调整
        chunkProgressTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        chunkProgressTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        chunkProgressTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        chunkProgressTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        chunkProgressTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        chunkProgressTable.getColumnModel().getColumn(5).setPreferredWidth(150);
        
        JScrollPane chunkProgressScrollPane = new JScrollPane(chunkProgressTable);
        chunkProgressPanel.add(chunkProgressScrollPane, BorderLayout.CENTER);
        
        // 为分块进度面板设置合适的高度，避免占用过多空间
        chunkProgressPanel.setPreferredSize(new Dimension(0, 300));
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
            // 将英文状态转换为中文显示
            String statusStr;
            switch (taskInfo.getStatus()) {
                case WAITING:
                    statusStr = "暂停";
                    break;
                case DOWNLOADING:
                    statusStr = "下载中";
                    break;
                case COMPLETED:
                    statusStr = "已完成";
                    break;
                case CANCELED:
                    statusStr = "已取消";
                    break;
                case FAILED:
                    statusStr = "失败";
                    break;
                default:
                    statusStr = taskInfo.getStatus().toString();
            }
            totalProgressBar.setString(progress + "% - " + statusStr);
            
            // 更新已下载大小
            String downloadedSizeStr = formatFileSize(taskInfo.getDownloadedSize()) + "/ " + formatFileSize(taskInfo.getFileSize());
            downloadedSizeField.setText(downloadedSizeStr);
            
            // 更新下载速度
            double currentSpeed = taskInfo.getDownloadSpeed();
            String speedStr;
            
            // 如果是下载中状态，实时更新速度
            if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                if (currentSpeed > 0) {
                    averageDownloadSpeed = (averageDownloadSpeed * 0.9) + (currentSpeed * 0.1); // 平滑计算平均速度
                    speedStr = df.format(currentSpeed) + " KB/s";
                } else {
                    speedStr = "0.00 KB/s";
                }
            } 
            // 如果是已完成状态，显示平均下载速度
            else if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.COMPLETED) {
                speedStr = df.format(averageDownloadSpeed) + " KB/s";
            } 
            // 其他状态显示0
            else {
                speedStr = "0.00 KB/s";
            }
            
            speedField.setText(speedStr);
            
            // 更新剩余时间
            String remainingTimeStr;
            long remainingTime = taskInfo.getEstimatedTimeRemaining();
            
            if (taskInfo.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                if (remainingTime > 0) {
                    remainingTimeStr = formatTime(remainingTime);
                } else if (remainingTime == 0) {
                    remainingTimeStr = "00:00:00";
                } else {
                    remainingTimeStr = "--:--:--";
                }
            } 
            // 下载完成或其他状态显示00:00:00
            else {
                remainingTimeStr = "00:00:00";
            }
            
            remainingTimeField.setText(remainingTimeStr);
            
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
            return size + "B";
        } else if (size < 1024 * 1024) {
            return df.format(size / 1024.0) + "KB";
        } else if (size < 1024 * 1024 * 1024) {
            return df.format(size / (1024.0 * 1024.0)) + "MB";
        } else {
            return df.format(size / (1024.0 * 1024.0 * 1024.0)) + "GB";
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
    
    /**
     * 将秒数格式化为HH:mm:ss格式
     * @param seconds 秒数
     * @return 格式化后的时间字符串
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}
