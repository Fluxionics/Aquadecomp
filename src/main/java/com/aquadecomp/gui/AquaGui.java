package com.aquadecomp.gui;

import com.aquadecomp.core.Pipeline;
import com.aquadecomp.core.PipelineListener;
import com.aquadecomp.core.PipelineResult;
import com.aquadecomp.core.ReportWriter;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;

public final class AquaGui {

    private JFrame frame;
    private JTextField apkField;
    private JTextField outField;
    private JCheckBox deobfCheck;
    private JCheckBox resourcesCheck;
    private JButton runButton;
    private JButton openFolderButton;
    private JProgressBar progressBar;
    private JTextArea reportArea;
    private JTextArea logArea;
    private JLabel statusLabel;

    private Path lastOutputDir;

    public static void launch(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                FlatLightLaf.setup();
            } catch (Exception e) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }
            }
            new AquaGui().createAndShow();
        });
    }

    private void createAndShow() {
        frame = new JFrame("Aquadecomp (BETA) — Descompilador de APKs");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 640));
        frame.setLocationByPlatform(true);

        frame.add(buildTopPanel(), BorderLayout.NORTH);
        frame.add(buildCenterPanel(), BorderLayout.CENTER);
        frame.add(buildBottomPanel(), BorderLayout.SOUTH);

        frame.pack();
        frame.setSize(1000, 700);
        frame.setVisible(true);
    }

    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        panel.add(new JLabel("APK:"), c);
        apkField = new JTextField(46);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(apkField, c);
        JButton browseApk = new JButton("Examinar…");
        browseApk.addActionListener(e -> chooseFile());
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(browseApk, c);

        c.gridx = 0; c.gridy = 1;
        panel.add(new JLabel("Salida:"), c);
        outField = new JTextField(46);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(outField, c);
        JButton browseOut = new JButton("Examinar…");
        browseOut.addActionListener(e -> chooseDir());
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(browseOut, c);

        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        deobfCheck = new JCheckBox("Activar desofuscación", true);
        resourcesCheck = new JCheckBox("Incluir recursos", true);
        opts.add(deobfCheck);
        opts.add(resourcesCheck);
        c.gridx = 1; c.gridy = 2;
        panel.add(opts, c);

        return panel;
    }

    private JPanel buildCenterPanel() {
        reportArea = new JTextArea();
        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        reportArea.setText("  Selecciona un APK y pulsa «Descompilar» para empezar.\n");

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Reporte", new JScrollPane(reportArea));
        tabs.addTab("Registro", new JScrollPane(logArea));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(tabs, BorderLayout.CENTER);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        return wrap;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(100, 22));

        statusLabel = new JLabel("Listo");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        runButton = new JButton("Descompilar");
        runButton.addActionListener(e -> runPipeline());

        openFolderButton = new JButton("Abrir carpeta de salida");
        openFolderButton.setEnabled(false);
        openFolderButton.addActionListener(e -> openOutput());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(openFolderButton);
        right.add(runButton);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(progressBar, BorderLayout.CENTER);
        top.add(statusLabel, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);
        panel.add(right, BorderLayout.SOUTH);
        return panel;
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar APK");
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            apkField.setText(f.getAbsolutePath());
            if (outField.getText().isBlank()) {
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                outField.setText(new File(f.getParentFile(),
                        "aquadecomp-out/" + (dot > 0 ? name.substring(0, dot) : name)).getAbsolutePath());
            }
        }
    }

    private void chooseDir() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Directorio de salida");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            outField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void openOutput() {
        if (lastOutputDir == null) return;
        try {
            Desktop.getDesktop().open(lastOutputDir.toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "No se pudo abrir: " + e.getMessage(),
                    "Aquadecomp", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void runPipeline() {
        String apkText = apkField.getText().trim();
        if (apkText.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Selecciona un APK primero.",
                    "Aquadecomp", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path apk = Path.of(apkText);
        Path out = outField.getText().isBlank()
                ? Path.of("aquadecomp-out", "salida")
                : Path.of(outField.getText().trim());

        runButton.setEnabled(false);
        logArea.setText("");
        reportArea.setText("");
        progressBar.setValue(0);
        statusLabel.setText("Iniciando…");

        Pipeline.Options opts = new Pipeline.Options(apk, out,
                deobfCheck.isSelected(), resourcesCheck.isSelected(), 0);

        SwingWorker<PipelineResult, String> worker = new SwingWorker<>() {
            @Override
            protected PipelineResult doInBackground() {
                PipelineListener listener = new PipelineListener() {
                    @Override
                    public void onStage(String stage) {
                        publish("==> " + stage);
                        SwingUtilities.invokeLater(() -> statusLabel.setText(stage));
                    }

                    @Override
                    public void onProgress(int percent, String message) {
                        int p = Math.max(0, Math.min(100, percent));
                        setProgress(p);
                        publish(String.format("[%3d%%] %s", p, message));
                        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
                    }

                    @Override
                    public void onLog(String line) {
                        publish("    " + line);
                    }
                };
                return new Pipeline().run(opts, listener);
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String s : chunks) {
                    logArea.append(s + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }
            }

            @Override
            protected void done() {
                try {
                    PipelineResult result = get();
                    lastOutputDir = result.success() ? Path.of(result.outputDir()) : null;
                    openFolderButton.setEnabled(result.success());

                    reportArea.setText(buildReportText(result));
                    reportArea.setCaretPosition(0);

                    if (result.success()) {
                        try {
                            ReportWriter.writeJson(result, Path.of(result.outputDir()));
                            ReportWriter.writeMarkdown(result, Path.of(result.outputDir()));
                        } catch (Exception ignored) {
                        }
                        statusLabel.setText("Completado");
                        progressBar.setValue(100);
                    } else {
                        statusLabel.setText("Error: " + result.errorMessage());
                        JOptionPane.showMessageDialog(frame,
                                "Error: " + result.errorMessage(),
                                "Aquadecomp", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error inesperado");
                    JOptionPane.showMessageDialog(frame, e.getMessage(),
                            "Aquadecomp", JOptionPane.ERROR_MESSAGE);
                } finally {
                    runButton.setEnabled(true);
                }
            }
        };
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        worker.execute();
    }

    private String buildReportText(PipelineResult r) {
        if (!r.success()) {
            return "ERROR: " + r.errorMessage();
        }
        var e = r.effectiveness();
        var st = r.stats();
        var m = r.manifest();
        var o = r.obfuscation();

        StringBuilder sb = new StringBuilder();
        sb.append("══════════════════════════════════════════════════\n");
        sb.append(String.format("  EFECTIVIDAD GLOBAL: %.1f / 100%n", e.overallScore()));
        sb.append("══════════════════════════════════════════════════\n\n");
        if (r.inputType() != null) {
            sb.append("  Entrada     : ").append(r.inputType()).append('\n');
        }
        sb.append(String.format("  Cobertura       : %5.1f   %s%n", e.coverageScore(), e.coverageDetail()));
        sb.append(String.format("  Integridad      : %5.1f   %s%n", e.integrityScore(), e.integrityDetail()));
        sb.append(String.format("  Identificadores : %5.1f   %s%n", e.identifierScore(), e.identifierDetail()));
        if (e.resourceScore() >= 0) {
            sb.append(String.format("  Recursos        : %5.1f   %s%n", e.resourceScore(), e.resourceDetail()));
        }
        sb.append("\n──────────────── APLICACIÓN ────────────────\n");
        if (m.parsedOk()) {
            sb.append("  Paquete   : ").append(m.packageName()).append('\n');
            sb.append("  Versión   : ").append(m.versionName()).append('\n');
            sb.append("  Principal : ").append(m.mainActivity()).append('\n');
            sb.append("  Permisos  : ").append(m.permissions().size()).append('\n');
            for (String p : m.permissions()) sb.append("      · ").append(p).append('\n');
        } else {
            sb.append("  (manifiesto no legible: ").append(m.parseError()).append(")\n");
        }
        sb.append("\n──────────────── OFUSCACIÓN ───────────────\n");
        sb.append("  Nivel: ").append(o.level()).append('\n');
        for (String s : o.signals()) sb.append("  · ").append(s).append('\n');
        if (!r.structure().detectedPackers().isEmpty()) {
            sb.append("  Packers: ").append(String.join(", ", r.structure().detectedPackers())).append('\n');
        }
        sb.append("\n──────────────── ESTADÍSTICAS ─────────────\n");
        sb.append(String.format("  Clases     : %d totales / %d con código / %d sin código%n",
                st.classesTotal(), st.classesWithCode(), st.classesNoCode()));
        sb.append(String.format("  Errores    : %d  (avisos: %d)%n", st.errors(), st.warns()));
        sb.append(String.format("  Archivos   : %d .java, %d líneas%n", st.javaFiles(), st.sourceLines()));
        sb.append(String.format("  Duración   : %.1f s%n", st.durationMs() / 1000.0));
        if (r.recovery() != null) {
            var rec = r.recovery();
            sb.append("\n──────────────── RECUPERACIÓN ─────────────\n");
            sb.append(String.format("  Archivos   : %d procesados, %d modificados%n",
                    rec.filesProcessed(), rec.filesModified()));
            for (var item : rec.items().entrySet()) {
                sb.append("  ").append(item.getKey()).append(": ")
                        .append(item.getValue()).append('\n');
            }
            if (rec.javacSampled() > 0) {
                sb.append(String.format("  javac      : %d/%d sin errores de sintaxis%n",
                        rec.javacCompiled(), rec.javacSampled()));
            } else {
                sb.append("  javac      : no disponible\n");
            }
            sb.append(String.format("  Kotlin     : %d clases | internas: %d%n",
                    rec.kotlinClasses(), rec.innerClasses()));
        }
        if (r.confidence() != null && r.confidence().total() > 0) {
            var c = r.confidence();
            sb.append("\n──────────────── CONFIANZA ─────────────────\n");
            sb.append(String.format("  Media      : %.1f/100 en %d clases%n",
                    c.averageScore(), c.total()));
            sb.append(String.format("  Excelente  : %d | Bueno: %d | Aceptable: %d%n",
                    c.excellent(), c.good(), c.acceptable()));
            sb.append(String.format("  Pobre      : %d | No confiable: %d%n",
                    c.poor(), c.unreliable()));
            sb.append(String.format("  Estructura válida: %d/%d (heurística)%n",
                    c.structurallyValid(), c.total()));
        }
        sb.append("\n──────────────── FORTALEZAS ────────────────\n");
        for (String s : e.strengths()) sb.append("  + ").append(s).append('\n');
        sb.append("\n──────────────── LIMITACIONES ──────────────\n");
        for (String s : e.limitations()) sb.append("  - ").append(s).append('\n');
        sb.append("\n──────────────── METODOLOGÍA ───────────────\n");
        sb.append("  ").append(e.methodology()).append('\n');
        return sb.toString();
    }
}
