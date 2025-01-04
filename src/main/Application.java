package main;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Arrays;

public class Application extends JFrame {
    private final JList<String> modelList;
    private final JList<String> dataList;
    private final DefaultTableModel tableModel;
    private final JButton runModelButton;
    private final JButton runScriptFromFileButton;
    private final JButton createAdHocScriptButton;
    private Controller controller;

    public Application() {
        setTitle("Modelling framework");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Select model and data"));

        DefaultListModel<String> modelListModel = new DefaultListModel<>();
        modelList = new JList<>(modelListModel);
        loadModels(modelListModel);

        DefaultListModel<String> dataListModel = new DefaultListModel<>();
        dataList = new JList<>(dataListModel);
        loadDataFiles(dataListModel);

        JPanel selectionPanel = new JPanel(new GridLayout(1, 2));
        selectionPanel.add(new JScrollPane(modelList));
        selectionPanel.add(new JScrollPane(dataList));

        leftPanel.add(selectionPanel, BorderLayout.CENTER);

        runModelButton = new JButton("Run model");
        leftPanel.add(runModelButton, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(300, 600));

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Results"));

        tableModel = new DefaultTableModel(){
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable resultTable = new JTable(tableModel);
        rightPanel.add(new JScrollPane(resultTable), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        runScriptFromFileButton = new JButton("Run script from file");
        createAdHocScriptButton = new JButton("Create and run script");
        buttonPanel.add(runScriptFromFileButton);
        buttonPanel.add(createAdHocScriptButton);

        rightPanel.add(buttonPanel, BorderLayout.SOUTH);
        rightPanel.setPreferredSize(new Dimension(500, 600));

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        setupActions();
        setVisible(true);
    }

    private void setupActions() {
        runModelButton.addActionListener(e -> {
            String modelName = modelList.getSelectedValue();
            String dataFile = dataList.getSelectedValue();

            if (modelName == null || dataFile == null) {
                JOptionPane.showMessageDialog(this, "Please select a model and a data file.");
                return;
            }

            try {
                controller = new Controller("models." + modelName);
                controller.readDataFrom("src/data/" + dataFile).runModel();
                updateResultsTable();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        runScriptFromFileButton.addActionListener(e -> {
            if (controller == null) {
                JOptionPane.showMessageDialog(this, "Please run a model first.");
                return;
            }
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File("src/scripts"));
            int choice = fileChooser.showOpenDialog(this);
            if (choice == JFileChooser.APPROVE_OPTION) {
                File scriptFile = fileChooser.getSelectedFile();
                try {
                    controller.runScriptFromFile(scriptFile.getAbsolutePath());
                    updateResultsTable();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });

        createAdHocScriptButton.addActionListener(e -> {
            if (controller == null) {
                JOptionPane.showMessageDialog(this, "Please run a model first.");
                return;
            }

            JTextArea scriptArea = new JTextArea(10, 40);
            JScrollPane scrollPane = new JScrollPane(scriptArea);

            int result = JOptionPane.showConfirmDialog(
                    this,
                    scrollPane,
                    "Write and run script",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (result == JOptionPane.OK_OPTION) {
                String script = scriptArea.getText();

                if (script.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Script cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    controller.runScript(script);
                    updateResultsTable();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void updateResultsTable() {
        String[] lines = controller.getResultsAsTsv().split("\n");
        String[] headers = lines[0].split("\t");
        tableModel.setColumnIdentifiers(headers);

        tableModel.setRowCount(0);
        for (int i = 1; i < lines.length; i++) {
            tableModel.addRow(lines[i].split("\t"));
        }
    }

    private void loadModels(DefaultListModel<String> modelListModel) {
        File modelsDir = new File("src/models");
        if (modelsDir.exists() && modelsDir.isDirectory()) {
            String[] models = modelsDir.list((dir, name) -> name.endsWith(".java"));
            if (models != null) {
                Arrays.stream(models)
                        .filter(name -> name != null)
                        .map(name -> name.replace(".java", ""))
                        .forEach(modelListModel::addElement);
            }
        }
    }

    private void loadDataFiles(DefaultListModel<String> dataListModel) {
        File dataDir = new File("src/data");
        if (dataDir.exists() && dataDir.isDirectory()) {
            String[] dataFiles = dataDir.list((dir, name) -> name.endsWith(".txt"));
            if (dataFiles != null) {
                Arrays.stream(dataFiles)
                        .filter(name -> name != null)
                        .forEach(name->dataListModel.addElement(name));
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Application::new);
    }
}
