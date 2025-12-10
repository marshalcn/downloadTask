package com.example.download.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.Timer;
import com.example.download.core.MultiThreadDownloader;
import com.example.download.manager.ConfigManager;
import com.example.download.manager.TaskManager;
import com.example.download.model.DownloadTaskInfo;

public class DownloadGUI extends JFrame {
    private JTextField urlTextField;
    private JButton startButton;
    private JButton createTaskButton;
    
    // 设置组件
    private JTextField savePathTextField;
    private JSpinner threadCountSpinner;
    private JButton browseButton;
    private JButton saveButton;
    private JButton cancelButton;
    
    private MultiThreadDownloader downloader;
    private ConfigManager configManager;
    private TaskManager taskManager;
    private JTable taskTable;
    private DefaultTableModel taskTableModel;
    private Timer refreshTimer; // 用于刷新任务列表的定时器

    public DownloadGUI() {
        downloader = new MultiThreadDownloader();
        configManager = new ConfigManager();
        taskManager = new TaskManager();
        initializeUI();
        
        // 启动定时器，每秒刷新一次任务列表
        refreshTimer = new Timer(1000, e -> refreshTaskList());
        refreshTimer.start();
    }

    private void initializeUI() {
        setTitle("多线程下载工具");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        // 创建主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 创建标签栏
        JTabbedPane tabbedPane = new JTabbedPane();

        // 第一个标签：创建下载任务
        JPanel createTaskPanel = new JPanel();
        createTaskPanel.setLayout(new BoxLayout(createTaskPanel, BoxLayout.Y_AXIS));
        createTaskPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // 创建任务按钮
        createTaskButton = new JButton("创建下载任务");
        createTaskButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        createTaskButton.addActionListener(e -> showCreateTaskDialog());
        createTaskButton.setPreferredSize(new Dimension(200, 40));
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(createTaskButton);
        
        // 任务列表面板
        JPanel taskPanel = new JPanel(new BorderLayout());
        taskPanel.setBorder(BorderFactory.createTitledBorder("任务列表"));
        

        

        
        // 创建任务表格
        String[] columnNames = {"文件名", "状态", "进度", "速度", "剩余时间", "添加时间", "完成时间"};
        taskTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        taskTable = new JTable(taskTableModel);
        
        // 添加任务点击监听器
        taskTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // 双击事件
                    int selectedRow = taskTable.getSelectedRow();
                    if (selectedRow >= 0) {
                        // 获取选中的任务
                        String fileName = (String) taskTableModel.getValueAt(selectedRow, 0);
                        DownloadTaskInfo selectedTask = taskManager.getAllTasks().stream()
                                .filter(task -> task.getFileName().equals(fileName))
                                .findFirst()
                                .orElse(null);
                        
                        if (selectedTask != null) {
                            // 显示下载详情页
                            DownloadDetailDialog detailDialog = new DownloadDetailDialog(DownloadGUI.this, selectedTask);
                            detailDialog.setVisible(true);
                        }
                    }
                }
            }
        });
        
        // 设置表格列宽
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(150); // 文件名
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // 状态
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // 进度
        taskTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // 速度
        taskTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // 剩余时间
        taskTable.getColumnModel().getColumn(5).setPreferredWidth(120); // 添加时间
        taskTable.getColumnModel().getColumn(6).setPreferredWidth(120); // 完成时间
        
        // 设置表格自动调整
        taskTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane taskScrollPane = new JScrollPane(taskTable);
        taskPanel.add(taskScrollPane, BorderLayout.CENTER);
        taskPanel.setMinimumSize(new Dimension(0, 150));
        taskPanel.setPreferredSize(new Dimension(0, 150));
        
        // 添加组件到创建任务面板
        createTaskPanel.add(buttonPanel);
        createTaskPanel.add(Box.createVerticalStrut(10));
        createTaskPanel.add(taskPanel);

        // 第二个标签：设置
        JPanel settingsTabPanel = new JPanel();
        settingsTabPanel.setLayout(new GridBagLayout());
        settingsTabPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // 保存路径标签
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        settingsTabPanel.add(new JLabel("默认保存路径:"), gbc);

        // 保存路径文本框
        savePathTextField = new JTextField(configManager.getDefaultDownloadPath());
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1;
        settingsTabPanel.add(savePathTextField, gbc);

        // 浏览按钮
        browseButton = new JButton("浏览");
        browseButton.addActionListener(e -> browseSavePath());
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0;
        settingsTabPanel.add(browseButton, gbc);

        // 线程数标签
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        settingsTabPanel.add(new JLabel("默认下载线程数:"), gbc);

        // 线程数微调器
        SpinnerNumberModel threadCountModel = new SpinnerNumberModel(
                configManager.getDefaultThreadCount(), 1, 32, 1);
        threadCountSpinner = new JSpinner(threadCountModel);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        settingsTabPanel.add(threadCountSpinner, gbc);

        // 按钮面板
        JPanel settingsButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        // 保存按钮
        saveButton = new JButton("保存");
        saveButton.addActionListener(e -> saveSettings());
        settingsButtonPanel.add(saveButton);

        // 取消按钮
        cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> resetSettings());
        settingsButtonPanel.add(cancelButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.EAST;
        settingsTabPanel.add(settingsButtonPanel, gbc);

        // 添加标签到标签栏
        tabbedPane.addTab("创建任务", createTaskPanel);
        tabbedPane.addTab("设置", settingsTabPanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // 设置内容面板
        setContentPane(mainPanel);

        // 设置窗口关闭操作
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        // 添加窗口关闭监听
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 停止定时器
                if (refreshTimer != null) {
                    refreshTimer.stop();
                }
                
                // 退出程序
                dispose();
            }
        });
    }

    /**
     * 显示创建下载任务对话框
     */
    private void showCreateTaskDialog() {
        // 创建下载任务对话框
        JDialog createTaskDialog = new JDialog(this, "创建下载任务", true);
        createTaskDialog.setSize(400, 200);
        createTaskDialog.setLocationRelativeTo(this);
        createTaskDialog.setResizable(false);
        
        // 对话框内容面板
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // URL标签和输入框
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(new JLabel("下载链接:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        JTextField dialogUrlTextField = new JTextField(25);
        contentPanel.add(dialogUrlTextField, gbc);
        
        // 开始下载按钮
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton dialogStartButton = new JButton("开始下载");
        dialogStartButton.addActionListener(e -> {
            String url = dialogUrlTextField.getText().trim();
            if (!url.isEmpty()) {
                // 设置主界面的URL文本框
                urlTextField.setText(url);
                // 关闭对话框
                createTaskDialog.dispose();
                // 执行下载
                startDownload();
            } else {
                JOptionPane.showMessageDialog(createTaskDialog, "请输入下载URL", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        dialogStartButton.setPreferredSize(new Dimension(120, 30));
        contentPanel.add(dialogStartButton, gbc);
        
        createTaskDialog.add(contentPanel);
        createTaskDialog.setVisible(true);
    }
    
    /**
     * 浏览保存路径
     */
    private void browseSavePath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(new File(savePathTextField.getText()));
        if (fileChooser.showDialog(this, "选择") == JFileChooser.APPROVE_OPTION) {
            savePathTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * 保存设置
     */
    private void saveSettings() {
        String savePath = savePathTextField.getText().trim();
        int threadCount = (int) threadCountSpinner.getValue();

        if (savePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择保存路径", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            configManager.setDefaultDownloadPath(savePath);
            configManager.setDefaultThreadCount(threadCount);
            JOptionPane.showMessageDialog(this, "设置保存成功", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存设置失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 重置设置
     */
    private void resetSettings() {
        savePathTextField.setText(configManager.getDefaultDownloadPath());
        threadCountSpinner.setValue(configManager.getDefaultThreadCount());
    }

    private void startDownload() {
        String url = urlTextField.getText().trim();
        String savePath = configManager.getDefaultDownloadPath();
        int threadCount = configManager.getDefaultThreadCount();

        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入下载链接", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (savePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在设置中配置默认保存路径", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 创建下载任务信息
        DownloadTaskInfo taskInfo = new DownloadTaskInfo();
        taskInfo.setUrl(url);
        taskInfo.setSavePath(savePath);
        taskInfo.setThreadCount(threadCount);
        
        // 添加任务到任务管理器
        taskManager.addTask(taskInfo);
        
        // 启动下载线程
        new Thread(() -> {
            try {
                // 执行下载
                downloader.download(taskInfo, null);
                
                // 更新任务信息
                taskManager.updateTask(taskInfo);

                // 下载完成
                JOptionPane.showMessageDialog(DownloadGUI.this, "文件下载完成！", "提示", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                // 更新任务状态为失败
                taskInfo.setStatus(DownloadTaskInfo.TaskStatus.FAILED);
                taskManager.updateTask(taskInfo);
                
                JOptionPane.showMessageDialog(DownloadGUI.this, "下载失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void appendLog(String message) {
        // 日志功能已移除
    }

    private void disableInputControls() {
        SwingUtilities.invokeLater(() -> {
            createTaskButton.setEnabled(false);
            // 设置标签页中的控件不受影响
        });
    }

    private void enableInputControls() {
        SwingUtilities.invokeLater(() -> {
            createTaskButton.setEnabled(true);
            // 设置标签页中的控件不受影响
        });
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
    
    /**
     * 格式化时间字符串
     * 
     * @param date 日期
     * @return 格式化后的时间字符串
     */
    private String formatDateTime(Date date) {
        if (date == null) {
            return "-";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(date);
    }
    
    /**
     * 格式化速度字符串
     * 
     * @param speed 速度（KB/s）
     * @return 格式化后的速度字符串
     */
    private String formatSpeed(double speed) {
        if (speed <= 0) {
            return "-";
        }
        return new DecimalFormat("0.0").format(speed) + " KB/s";
    }
    
    /**
     * 格式化剩余时间字符串
     * 
     * @param seconds 剩余时间（秒）
     * @return 格式化后的剩余时间字符串
     */
    private String formatRemainingTime(long seconds) {
        if (seconds < 0) {
            return "-";
        } else if (seconds == 0) {
            return "00:00";
        }
        
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }
    
    /**
     * 刷新任务列表
     */
    private void refreshTaskList() {
        // 获取排序后的任务列表
        java.util.List<DownloadTaskInfo> sortedTasks = taskManager.getSortedTasks();
        
        // 更新任务表格
        SwingUtilities.invokeLater(() -> {
            taskTableModel.setRowCount(0); // 清空表格
            
            for (DownloadTaskInfo task : sortedTasks) {
                String[] rowData = new String[7];
                rowData[0] = task.getFileName();
                rowData[1] = task.getStatus().toString();
                rowData[2] = task.getProgress() + "%";
                rowData[3] = formatSpeed(task.getDownloadSpeed());
                rowData[4] = formatRemainingTime(task.getEstimatedTimeRemaining());
                rowData[5] = formatDateTime(task.getAddTime());
                rowData[6] = formatDateTime(task.getCompletedTime());
                
                taskTableModel.addRow(rowData);
            }
        });
    }

    // 创建进度监听器接口 (已简化)
    public interface ProgressListener {
        void onProgress(long downloaded, long total);
        void onLog(String message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            DownloadGUI frame = new DownloadGUI();
            frame.setVisible(true);
        });
    }
}