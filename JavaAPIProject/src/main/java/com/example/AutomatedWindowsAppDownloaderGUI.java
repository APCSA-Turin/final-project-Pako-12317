package com.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.*;

public class AutomatedWindowsAppDownloaderGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    // App data structure
    static class App {
        String id;
        String name;
        String source;
        String version;

        App(String id, String name, String source, String version) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.version = version;
        }

        @Override
        public String toString() {
            return String.format("%s (%s) [%s] v%s", name, id, source, version);
        }
    }

    private DefaultListModel<App> appListModel;
    private JList<App> appJList;
    private JButton searchButton, installButton;
    private JTextField searchField;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JLabel timeLabel;
    private JTextArea descriptionArea;

    private javax.swing.Timer timer;
    private Instant startTime;

    public AutomatedWindowsAppDownloaderGUI() {
        setTitle("Windows App Installer with Search");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Search panel
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchField = new JTextField();
        searchButton = new JButton("Search");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        // App list
        appListModel = new DefaultListModel<>();
        appJList = new JList<>(appListModel);
        appJList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(appJList);
        listScrollPane.setPreferredSize(new Dimension(400, 400));

        // Description area
        descriptionArea = new JTextArea(8, 40);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(BorderFactory.createTitledBorder("App Description"));
        JScrollPane descScrollPane = new JScrollPane(descriptionArea);

        // Install button
        installButton = new JButton("Install Selected");

        // Log area
        logArea = new JTextArea(10, 60);
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);

        // Progress bar and timer
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        timeLabel = new JLabel("Elapsed: 00:00");
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(timeLabel, BorderLayout.EAST);

        // Layout
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        leftPanel.add(descScrollPane, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.add(logScrollPane, BorderLayout.CENTER);
        rightPanel.add(installButton, BorderLayout.SOUTH);

        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.CENTER);
        mainPanel.add(progressPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Timer for elapsed time
        timer = new javax.swing.Timer(1000, e -> updateTimeDisplay());

        // Event handlers
        searchButton.addActionListener(e -> searchWingetApps());
        installButton.addActionListener(e -> installSelectedApps());

        // Description fetch on selection
        appJList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && appJList.getSelectedValue() != null) {
                    App selectedApp = appJList.getSelectedValue();
                    fetchAndDisplayDescription(selectedApp.id);
                } else if (appJList.getSelectedValue() == null) {
                    descriptionArea.setText("");
                }
            }
        });
    }

    private void searchWingetApps() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a search query.");
            return;
        }
        appListModel.clear();
        log("Searching winget for: " + query);

        new SwingWorker<List<App>, Void>() {
            @Override
            protected List<App> doInBackground() throws Exception {
                return executeWingetSearch(query);
            }

            @Override
            protected void done() {
                try {
                    List<App> results = get();
                    if (results.isEmpty()) {
                        log("No results found.");
                    } else {
                        log("Found " + results.size() + " apps.");
                        for (App app : results) {
                            appListModel.addElement(app);
                        }
                    }
                } catch (Exception e) {
                    log("Search failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    private List<App> executeWingetSearch(String query) throws IOException, InterruptedException {
        List<App> apps = new ArrayList<>();
        Process process = new ProcessBuilder("winget", "search", "-q", query).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            boolean headerPassed = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!headerPassed) {
                    if (line.startsWith("Name")) headerPassed = true;
                    continue;
                }
                if (line.trim().isEmpty()) continue;
                // Parse winget output: Name  Id  Version  Source
                String[] parts = line.split(" {2,}");
                if (parts.length >= 4) {
                    apps.add(new App(
                        parts[1].trim(),  // ID
                        parts[0].trim(),  // Name
                        parts[3].trim(),  // Source
                        parts[2].trim()   // Version
                    ));
                }
            }
        }
        process.waitFor();
        return apps;
    }

    private void fetchAndDisplayDescription(String appId) {
        descriptionArea.setText("Fetching description...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return getWingetDescription(appId);
            }

            @Override
            protected void done() {
                try {
                    String desc = get();
                    descriptionArea.setText(desc);
                } catch (Exception e) {
                    descriptionArea.setText("Failed to fetch description: " + e.getMessage());
                }
            }
        }.execute();
    }

    private String getWingetDescription(String appId) {
        StringBuilder desc = new StringBuilder();
        try {
            Process process = new ProcessBuilder("winget", "show", "--id", appId).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean foundDescription = false;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().toLowerCase().startsWith("description")) {
                        foundDescription = true;
                        // The line itself may be: "Description : Some text"
                        int idx = line.indexOf(":");
                        if (idx >= 0 && idx < line.length() - 1) {
                            desc.append(line.substring(idx + 1).trim()).append("\n");
                        }
                    } else if (foundDescription) {
                        // Keep reading until a blank line or a new key
                        if (line.trim().isEmpty() || line.contains(":")) break;
                        desc.append(line.trim()).append("\n");
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            desc.append("Error fetching description: ").append(e.getMessage());
        }
        if (desc.length() == 0) {
            desc.append("No description available.");
        }
        return desc.toString().trim();
    }

    private void installSelectedApps() {
        List<App> selectedApps = appJList.getSelectedValuesList();
        if (selectedApps.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select one or more applications to install.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        installButton.setEnabled(false);
        progressBar.setValue(0);
        timeLabel.setText("Elapsed: 00:00");
        logArea.setText("");

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                for (App app : selectedApps) {
                    publish("Starting installation for: " + app.toString());
                    boolean success = false;
                    startTime = Instant.now();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(0);
                        timer.start();
                    });
                    if (app.source.equalsIgnoreCase("winget") || app.source.equalsIgnoreCase("msstore")) {
                        success = installWingetApp(app);
                    } else if (app.source.equalsIgnoreCase("url")) {
                        publish("Direct URL installs not implemented in this demo.");
                        success = false;
                    }
                    SwingUtilities.invokeLater(() -> {
                        timer.stop();
                        progressBar.setValue(100);
                        updateTimeDisplay();
                    });
                    publish(success ? "SUCCESS: Installed " + app.name : "FAILURE: Could not install " + app.name);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }

            @Override
            protected void done() {
                installButton.setEnabled(true);
                log("All installations complete.");
            }
        }.execute();
    }

    private boolean installWingetApp(App app) {
        try {
            List<String> command = new ArrayList<>(Arrays.asList(
                "winget", "install", "--id", app.id,
                "--silent", "--accept-package-agreements", "--accept-source-agreements", "--exact"
            ));
            if (app.source.equalsIgnoreCase("msstore")) {
                command.add("--source");
                command.add("msstore");
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Simulate progress (since winget doesn't output progress)
            new Thread(() -> {
                int progress = 0;
                while (process.isAlive() && progress < 95) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {}
                    final int prog = progress += 5;
                    SwingUtilities.invokeLater(() -> progressBar.setValue(prog));
                }
            }).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log(line);
                }
            }

            int exitCode = process.waitFor();
            SwingUtilities.invokeLater(() -> progressBar.setValue(100));
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log("ERROR: " + e.getMessage());
            return false;
        }
    }

    private void updateTimeDisplay() {
        if (startTime != null) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            long minutes = elapsed.toMinutes();
            long seconds = elapsed.minusMinutes(minutes).getSeconds();
            timeLabel.setText(String.format("Elapsed: %02d:%02d", minutes, seconds));
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AutomatedWindowsAppDownloaderGUI().setVisible(true));
    }
}