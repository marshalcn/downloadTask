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
import java.util.List;
import javax.swing.Timer;
import com.example.download.core.MultiThreadDownloader;
import com.example.download.core.MultiThreadDownloader.DownloadRange;
import com.example.download.manager.ConfigManager;
import com.example.download.manager.TaskManager;
import com.example.download.model.DownloadTaskInfo;
import com.example.download.ui.DownloadDetailDialog;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public class DownloadGUI extends JFrame {
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
    private java.util.Set<String> selectedTaskIds; // 用于保存选中的任务ID
    private boolean isRestoringSelection = false; // 用于指示当前是否正在恢复选中状态

    public DownloadGUI() {
        downloader = new MultiThreadDownloader();
        configManager = new ConfigManager();
        taskManager = new TaskManager();
        selectedTaskIds = new java.util.HashSet<>();
        initializeUI();
        
        // 启动定时器，每秒刷新一次任务列表
        refreshTimer = new Timer(1000, e -> refreshTaskList());
        refreshTimer.start();
    }
    
    /**
     * 批量删除选中的任务
     */
    private void performBatchDelete() {
        java.util.List<DownloadTaskInfo> tasksToDelete = new java.util.ArrayList<>();
        
        // 获取所有任务
        java.util.List<DownloadTaskInfo> allTasks = taskManager.getAllTasks();
        
        // 遍历表格的每一行，检查复选框是否被选中
        for (int i = 0; i < taskTableModel.getRowCount(); i++) {
            Boolean isChecked = (Boolean) taskTableModel.getValueAt(i, 0);
            if (Boolean.TRUE.equals(isChecked)) {
                // 获取该行对应的任务ID（隐藏列）
                String taskId = (String) taskTableModel.getValueAt(i, 8);
                DownloadTaskInfo taskToDelete = allTasks.stream()
                        .filter(task -> task != null && task.getId().equals(taskId))
                        .findFirst()
                        .orElse(null);
                
                if (taskToDelete != null) {
                    tasksToDelete.add(taskToDelete);
                }
            }
        }
        
        // 删除选中的任务
        if (!tasksToDelete.isEmpty()) {
            // 创建自定义对话框，包含复选框
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("确定要删除选中的" + tasksToDelete.size() + "个任务吗？"), BorderLayout.NORTH);
            
            JCheckBox deleteLocalFileCheckBox = new JCheckBox("同时删除本地文件", true);
            panel.add(deleteLocalFileCheckBox, BorderLayout.CENTER);
            
            int confirm = JOptionPane.showConfirmDialog(this, panel, "确认删除", JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
                for (DownloadTaskInfo task : tasksToDelete) {
                                String savePath = task.getSavePath();
                                String taskId = task.getId();
                                
                                // 如果任务未完成，清理临时文件和目录
                                if (task.getStatus() != DownloadTaskInfo.TaskStatus.COMPLETED) {
                                    if (savePath != null && taskId != null) {
                                        try {
                                            downloader.cleanupTaskTempFiles(savePath, taskId);
                                        } catch (Exception e) {
                                            // 记录异常日志
                                            appendLog("清理任务" + taskId + "的临时文件和目录时出现异常: " + e.getMessage());
                                        }
                                    } else {
                                        // 记录日志，说明无法清理临时文件的原因
                                        appendLog("无法清理任务" + taskId + "的临时文件：保存路径或任务ID为空");
                                    }
                                }
                                
                                // 如果复选框被选中，删除本地文件
                                if (deleteLocalFileCheckBox.isSelected()) {
                                    String fileName = task.getFileName();
                                    // 检查savePath和fileName是否为null
                                    if (savePath != null && fileName != null) {
                                        File file = new File(savePath, fileName);
                                        if (file.exists()) {
                                            try {
                                                file.delete();
                                            } catch (Exception e) {
                                                // 记录异常日志
                                                appendLog("删除文件" + fileName + "时出现异常: " + e.getMessage());
                                            }
                                        }
                                    } else {
                                        // 记录日志，说明无法删除文件的原因
                                        appendLog("无法删除任务" + task.getId() + "的本地文件：保存路径或文件名为空");
                                    }
                                }
                                // 无论文件删除是否成功，都从本地任务记录删除
                                taskManager.deleteTask(task.getId());
                            }
                // 刷新任务列表
                refreshTaskList();
            }
        } else {
            JOptionPane.showMessageDialog(this, "请先选择要删除的任务", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    /**
     * 批量开始选中的任务
     */
    private void performBatchStart() {
        java.util.List<DownloadTaskInfo> tasksToStart = new java.util.ArrayList<>();
        
        // 获取所有任务
        java.util.List<DownloadTaskInfo> allTasks = taskManager.getAllTasks();
        
        // 调试日志：输出selectedTaskIds集合的内容
        System.out.println("performBatchStart called");
        System.out.println("selectedTaskIds size: " + selectedTaskIds.size());
        for (String taskId : selectedTaskIds) {
            System.out.println("Selected taskId: " + taskId);
        }
        
        // 直接使用selectedTaskIds集合来获取选中的任务
        for (DownloadTaskInfo task : allTasks) {
            if (task != null) {
                System.out.println("Checking task: " + task.getId());
                if (selectedTaskIds.contains(task.getId())) {
                    System.out.println("Task found in selectedTaskIds: " + task.getId());
                    if (task.getStatus() != DownloadTaskInfo.TaskStatus.COMPLETED) {
                        System.out.println("Task is not completed, adding to tasksToStart: " + task.getId());
                        tasksToStart.add(task);
                    } else {
                        System.out.println("Task is completed, skipping: " + task.getId());
                    }
                } else {
                    System.out.println("Task not found in selectedTaskIds: " + task.getId());
                }
            }
        }
        
        // 开始选中的任务
        if (!tasksToStart.isEmpty()) {
            System.out.println("Starting " + tasksToStart.size() + " tasks");
            for (DownloadTaskInfo task : tasksToStart) {
                System.out.println("Starting task: " + task.getId());
                downloader.startDownload(task);
            }
        } else {
            System.out.println("No tasks to start, showing message");
            if (selectedTaskIds.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请先选择要开始的任务", "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "所选任务已处于运行或完成状态，请选择等待或暂停状态的任务", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }
    
    /**
     * 批量暂停选中的任务
     */
    private void performBatchPause() {
        System.out.println("performBatchPause called");
        System.out.println("selectedTaskIds size: " + selectedTaskIds.size());
        
        boolean hasTasksToPause = false;
        List<DownloadTaskInfo> allTasks = taskManager.getAllTasks();
        
        for (DownloadTaskInfo task : allTasks) {
            System.out.println("Checking task: " + task.getId());
            if (selectedTaskIds.contains(task.getId())) {
                System.out.println("Task found in selectedTaskIds: " + task.getId());
                if (task.getStatus() == DownloadTaskInfo.TaskStatus.DOWNLOADING) {
                    // 只暂停正在下载的任务
                    downloader.pauseDownload(task);
                    hasTasksToPause = true;
                    System.out.println("Task paused: " + task.getId());
                } else {
                    System.out.println("Task is " + task.getStatus() + ", skipping: " + task.getId());
                }
            } else {
                System.out.println("Task not found in selectedTaskIds: " + task.getId());
            }
        }
        
        if (!hasTasksToPause) {
            System.out.println("No tasks to pause, showing message");
            if (selectedTaskIds.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请先选择要暂停的任务", "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "所选任务已处于非下载状态，请选择正在下载的任务", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        }
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
        String[] columnNames = {"选择", "文件名", "状态", "进度", "速度", "剩余时间", "添加时间", "完成时间", "任务ID"}; // 最后一列用于保存任务ID，隐藏显示
        taskTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 只有复选框列（第0列）可编辑
                return column == 0;
            }
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                // 第0列是布尔类型（复选框）
                if (columnIndex == 0) {
                    return Boolean.class;
                }
                return super.getColumnClass(columnIndex);
            }
        };
        taskTable = new JTable(taskTableModel);
        
        // 添加表格模型监听器，用于实时保存复选框状态变化
        taskTableModel.addTableModelListener(e -> {
            // System.out.println("TableModelListener triggered");
            // System.out.println("isRestoringSelection: " + isRestoringSelection);
            // System.out.println("Column: " + e.getColumn());
            // System.out.println("Row: " + e.getFirstRow());
            
            // 如果正在恢复选中状态，就不修改selectedTaskIds集合
            if (isRestoringSelection) {
                // System.out.println("Skipping because isRestoringSelection is true");
                return;
            }
            
            if (e.getColumn() == 0) { // 只监听复选框列（第0列）的变化
                // System.out.println("Processing checkbox column change");
                int row = e.getFirstRow();
                // 确保行索引是有效的（防止清空表格时触发）
                if (row >= 0 && row < taskTableModel.getRowCount()) {
                    // System.out.println("Row index is valid: " + row);
                    Boolean isSelected = (Boolean) taskTableModel.getValueAt(row, 0);
                    // System.out.println("isSelected: " + isSelected);
                    if (isSelected != null) {
                        // 直接从表格中获取任务ID
                        String taskId = (String) taskTableModel.getValueAt(row, 8);
                        // System.out.println("TaskId from table: " + taskId);
                        if (taskId != null) {
                            if (isSelected) {
                                selectedTaskIds.add(taskId);
                                // System.out.println("Added taskId to selectedTaskIds: " + taskId);
                                // System.out.println("selectedTaskIds size: " + selectedTaskIds.size());
                            } else {
                                selectedTaskIds.remove(taskId);
                                // System.out.println("Removed taskId from selectedTaskIds: " + taskId);
                                // System.out.println("selectedTaskIds size: " + selectedTaskIds.size());
                            }
                        }
                    }
                } else {
                    // System.out.println("Row index is invalid: " + row + ", table row count: " + taskTableModel.getRowCount());
                }
            } else {
                // System.out.println("Not processing because column is not 0");
            }
        });
        
        // 添加任务点击监听器
        taskTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // 双击事件
                    int selectedRow = taskTable.getSelectedRow();
                    if (selectedRow >= 0) {
                        // 获取选中的任务
                        String taskId = (String) taskTableModel.getValueAt(selectedRow, 8);
                        DownloadTaskInfo selectedTask = taskManager.getAllTasks().stream()
                                .filter(task -> task.getId().equals(taskId))
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
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(50); // 选择列
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(100); // 文件名
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(80); // 状态
        taskTable.getColumnModel().getColumn(3).setPreferredWidth(60); // 进度
        taskTable.getColumnModel().getColumn(4).setPreferredWidth(80); // 速度
        taskTable.getColumnModel().getColumn(5).setPreferredWidth(100); // 剩余时间
        taskTable.getColumnModel().getColumn(6).setPreferredWidth(120); // 添加时间
        taskTable.getColumnModel().getColumn(7).setPreferredWidth(120); // 完成时间
        taskTable.getColumnModel().getColumn(8).setMinWidth(0); // 任务ID列
        taskTable.getColumnModel().getColumn(8).setMaxWidth(0); // 隐藏任务ID列
        taskTable.getColumnModel().getColumn(8).setWidth(0);
        
        // 设置表格自动调整
        taskTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane taskScrollPane = new JScrollPane(taskTable);
        taskPanel.add(taskScrollPane, BorderLayout.CENTER);
        
        // 添加批量操作按钮面板
        JPanel batchOperationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton batchDeleteButton = new JButton("批量删除");
        JButton batchStartButton = new JButton("批量开始");
        JButton batchPauseButton = new JButton("批量暂停");
        
        batchDeleteButton.addActionListener(e -> performBatchDelete());
        batchStartButton.addActionListener(e -> performBatchStart());
        batchPauseButton.addActionListener(e -> performBatchPause());
        
        batchOperationPanel.add(batchDeleteButton);
        batchOperationPanel.add(batchStartButton);
        batchOperationPanel.add(batchPauseButton);
        
        taskPanel.add(batchOperationPanel, BorderLayout.SOUTH);
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
                // 关闭对话框
                createTaskDialog.dispose();
                // 执行下载
                startDownload(url);
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

    private void startDownload(String url) {
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
        
        // 在创建任务时就生成所有文件区块的索引并设置初始下载状态为未下载
        try {
            // 获取文件大小
            long fileSize = downloader.getFileSize(url);
            taskInfo.setFileSize(fileSize);
            
            // 创建保存目录和空文件
            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }
            
            // 从URL提取文件名
            String fileName = downloader.extractFileName(url);
            taskInfo.setFileName(fileName);
            String fullSavePath = new File(saveDir, fileName).getAbsolutePath();
            
            // 创建空文件并设置大小
            File downloadFile = new File(fullSavePath);
            try (RandomAccessFile raf = new RandomAccessFile(fullSavePath, "rw")) {
                raf.setLength(fileSize);
            }
            
            // 创建临时目录和索引文件
            String tempDirPath = saveDir.getAbsolutePath() + File.separator + ".temp-" + taskInfo.getId();
            File tempDir = new File(tempDirPath);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            File indexFile = new File(tempDir, "index.txt");
            
            // 生成所有文件区块的索引
            Set<DownloadRange> allRanges = downloader.generateAllRanges(fileSize);
            
            // 保存所有区块的索引（包括未下载的）到索引文件
            downloader.saveAllRanges(indexFile, allRanges);
            
            // 将索引文件复制到下载文件目录下，方便检查
            String downloadFileName = downloadFile.getName();
            String idxFileName = downloadFileName + ".idx";
            File idxFileCopy = new File(saveDir, idxFileName);
            Files.copy(indexFile.toPath(), idxFileCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            JOptionPane.showMessageDialog(this, "下载任务创建成功", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "创建下载任务失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 添加任务到任务管理器
        taskManager.addTask(taskInfo);
        
        // 启动下载线程
        new Thread(() -> {
            try {
                // 执行下载，使用配置的分块大小
                downloader.startDownload(taskInfo);
                
                // 更新任务信息
                taskManager.updateTask(taskInfo);
                
                // 显示开始下载的提示
                // JOptionPane.showMessageDialog(DownloadGUI.this, "开始下载文件！", "提示", JOptionPane.INFORMATION_MESSAGE);
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
            
            // 设置恢复选中状态标志
            isRestoringSelection = true;
            
            try {
                for (DownloadTaskInfo task : sortedTasks) {
                    // 对于已暂停的任务，从索引文件中获取最新的下载大小
                    if (task.getStatus() == DownloadTaskInfo.TaskStatus.PAUSED) {
                        long downloadedSize = downloader.getDownloadedSizeFromIndex(task);
                        if (downloadedSize != task.getDownloadedSize()) {
                            task.setDownloadedSize(downloadedSize);
                        }
                    }
                    
                    Object[] rowData = new Object[9];
                    // 恢复选中状态
                    rowData[0] = selectedTaskIds.contains(task.getId());
                    rowData[1] = task.getFileName();
                    // 将英文状态转换为中文显示
                    String statusStr;
                    switch (task.getStatus()) {
                        case WAITING:
                            statusStr = "等待中";
                            break;
                        case DOWNLOADING:
                            statusStr = "下载中";
                            break;
                        case COMPLETED:
                            statusStr = "已完成";
                            break;
                        case PAUSED:
                            statusStr = "已暂停";
                            break;
                        case CANCELED:
                            statusStr = "已取消";
                            break;
                        case FAILED:
                            statusStr = "失败";
                            break;
                        default:
                            statusStr = task.getStatus().toString();
                    }
                    rowData[2] = statusStr;
                    rowData[3] = task.getProgress() + "%";
                    rowData[4] = formatSpeed(task.getDownloadSpeed());
                    rowData[5] = formatRemainingTime(task.getEstimatedTimeRemaining());
                    rowData[6] = formatDateTime(task.getAddTime());
                    rowData[7] = formatDateTime(task.getCompletedTime());
                    rowData[8] = task.getId(); // 保存任务ID到隐藏列
                    
                    taskTableModel.addRow(rowData);
                }
            } finally {
                // 无论如何都要重置恢复选中状态标志
                isRestoringSelection = false;
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