package main;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Objects;

public class ModellingFrameworkGUI extends JFrame {
    private final JList<String> modelList;
    private final JList<String> dataList;
    private final JTable resultTable;
    private final DefaultTableModel tableModel;
    private final JButton runModelButton;
    private final JButton runScriptFromFileButton;
    private final JButton createAdHocScriptButton;
    private Controller controller;

    public ModellingFrameworkGUI() {
        setTitle("Modelling Framework");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Select Model and Data"));

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

        runModelButton = new JButton("Run Model");
        leftPanel.add(runModelButton, BorderLayout.SOUTH);

        leftPanel.setPreferredSize(new Dimension(250, 600));


        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Results"));

        tableModel = new DefaultTableModel();
        resultTable = new JTable(tableModel);
        rightPanel.add(new JScrollPane(resultTable), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        runScriptFromFileButton = new JButton("Run Script from File");
        createAdHocScriptButton = new JButton("Create and Run Script");
        buttonPanel.add(runScriptFromFileButton);
        buttonPanel.add(createAdHocScriptButton);

        rightPanel.add(buttonPanel, BorderLayout.SOUTH);

        rightPanel.setPreferredSize(new Dimension(650, 600));


        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        setupActions();
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

            JDialog dialog = new JDialog(this, "Script", true);
            dialog.setLayout(new BorderLayout());
            dialog.setSize(400, 300);
            dialog.setLocationRelativeTo(this);

            JTextArea scriptArea = new JTextArea();
            scriptArea.setLineWrap(true);
            scriptArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(scriptArea);
            dialog.add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okButton = new JButton("Ok");
            JButton cancelButton = new JButton("Cancel");
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);

            dialog.add(buttonPanel, BorderLayout.SOUTH);

            okButton.addActionListener(ev -> {
                String script = scriptArea.getText();
                try {
                    controller.runScript(script);
                    updateResultsTable();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
                dialog.dispose();
            });

            cancelButton.addActionListener(ev -> dialog.dispose());

            dialog.setVisible(true);
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
        File modelsDir = new File("out/production/UTPproject3/models");
        if (modelsDir.exists() && modelsDir.isDirectory()) {
            String[] models = modelsDir.list((dir, name) -> name.endsWith(".class"));
            if (models != null) {
                Arrays.stream(models)
                        .filter(Objects::nonNull)
                        .map(name -> name.replace(".class", ""))
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
                        .filter(Objects::nonNull)
                        .forEach(dataListModel::addElement);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ModellingFrameworkGUI gui = new ModellingFrameworkGUI();
            gui.setVisible(true);
        });
    }
}
