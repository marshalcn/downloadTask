package com.example.download.ui;

import com.example.download.manager.ConfigManager;
import com.example.download.manager.TaskManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

/**
 * 设置对话框类，用于管理应用程序设置
 */
public class SettingsDialog extends JDialog {
    private JTextField savePathTextField;
    private JSpinner threadCountSpinner;
    private JSpinner chunkSizeSpinner;
    private JButton browseButton;
    private JButton saveButton;
    private JButton cancelButton;
    
    private ConfigManager configManager;
    private TaskManager taskManager;

    /**
     * 构造函数
     * @param parent 父窗口
     * @param configManager 配置管理器
     * @param taskManager 任务管理器
     */
    public SettingsDialog(JFrame parent, ConfigManager configManager, TaskManager taskManager) {
        super(parent, "设置", true);
        this.configManager = configManager;
        this.taskManager = taskManager;
        initializeUI();
    }

    /**
     * 初始化UI组件
     */
    private void initializeUI() {
        setSize(600, 250);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(getParent());
        setResizable(false);

        // 创建主面板
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // 保存路径标签
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("默认保存路径:"), gbc);

        // 保存路径文本框
        savePathTextField = new JTextField(configManager.getDefaultDownloadPath());
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1;
        mainPanel.add(savePathTextField, gbc);

        // 浏览按钮
        browseButton = new JButton("浏览");
        browseButton.addActionListener(e -> browseSavePath());
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0;
        mainPanel.add(browseButton, gbc);

        // 线程数标签
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("默认下载线程数:"), gbc);

        // 线程数微调器
        SpinnerNumberModel threadCountModel = new SpinnerNumberModel(
                configManager.getDefaultThreadCount(), 1, 32, 1);
        threadCountSpinner = new JSpinner(threadCountModel);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        mainPanel.add(threadCountSpinner, gbc);

        // 分块大小标签
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("文件分块大小 (KB):"), gbc);

        // 分块大小微调器（转换为KB显示）
        int defaultChunkSizeKB = configManager.getDefaultChunkSize() / 1024;
        SpinnerNumberModel chunkSizeModel = new SpinnerNumberModel(
                defaultChunkSizeKB, 128, 10240, 128); // 128KB到10MB，步长128KB
        chunkSizeSpinner = new JSpinner(chunkSizeModel);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1;
        gbc.gridwidth = 2;
        mainPanel.add(chunkSizeSpinner, gbc);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        // 保存按钮
        saveButton = new JButton("保存");
        saveButton.addActionListener(e -> saveSettings());
        buttonPanel.add(saveButton);

        // 取消按钮
        cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        // 添加主面板和按钮面板
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * 浏览保存路径
     */
    private void browseSavePath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(new File(savePathTextField.getText()));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            savePathTextField.setText(selectedFile.getAbsolutePath());
        }
    }

    /**
     * 保存设置
     */
    private void saveSettings() {
        try {
            // 保存默认保存路径
            String savePath = savePathTextField.getText().trim();
            if (!savePath.isEmpty()) {
                configManager.setDefaultDownloadPath(savePath);
            }

            // 保存默认下载线程数
            int threadCount = (int) threadCountSpinner.getValue();
            configManager.setDefaultThreadCount(threadCount);

            // 保存默认分块大小（转换为字节保存）
            int chunkSizeKB = (int) chunkSizeSpinner.getValue();
            int chunkSizeBytes = chunkSizeKB * 1024;
            configManager.setDefaultChunkSize(chunkSizeBytes);

            JOptionPane.showMessageDialog(this, "设置已保存", "提示", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存设置失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}