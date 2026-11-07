package com.aquadecomp.gui;

import com.aquadecomp.core.ApkInspector;
import com.aquadecomp.core.ApkStructure;
import com.aquadecomp.core.ManifestInfo;
import com.aquadecomp.core.Pipeline;
import com.aquadecomp.core.PipelineListener;
import com.aquadecomp.core.PipelineResult;
import com.aquadecomp.core.ReportWriter;
import com.aquadecomp.input.InputNormalizer;
import com.aquadecomp.nativecode.NativeAnalyzer;
import com.aquadecomp.naming.NameInferer;
import com.aquadecomp.naming.NameReport;
import com.aquadecomp.rebuild.RebuildPipeline;
import com.aquadecomp.rebuild.RebuildReport;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class AquaGui {

    private static final String[] ACCIONES = {
            "Descompilar", "Analizar", "Recompilar", "Analizar nativos", "Sugerir nombres"
    };

    private JFrame frame;
    private JTabbedPane funciones;
    private JTextField apkField;
    private JTextField outField;
    private JCheckBox deobfCheck;
    private JCheckBox resourcesCheck;
    private JTextField apkAnalizarField;
    private JTextField dirRecompilarField;
    private JTextField salidaRecompilarField;
    private JTextField originalRecompilarField;
    private JCheckBox appCheck;
    private JTextField apkNativosField;
    private JTextField filtroNativosField;
    private JCheckBox ghidraCheck;
    private JTextField dirNombresField;
    private JTextField outNombresField;
    private JButton runButton;
    private JButton openFolderButton;
    private JProgressBar progressBar;
    private JTextArea reportArea;
    private JTextArea logArea;
    private JLabel statusLabel;

    private Path lastOutputDir;

    private record ResultadoTarea(String texto, Path carpeta, String error) {
    }

    private interface Tarea {
        ResultadoTarea ejecutar(PipelineListener listener) throws Exception;
    }

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

        funciones = buildFunciones();

        JPanel topWrap = new JPanel(new BorderLayout());
        topWrap.add(funciones, BorderLayout.NORTH);
        topWrap.add(new JSeparator(), BorderLayout.SOUTH);

        frame.add(topWrap, BorderLayout.NORTH);
        frame.add(buildCenterPanel(), BorderLayout.CENTER);
        frame.add(buildBottomPanel(), BorderLayout.SOUTH);

        funciones.addChangeListener(e ->
                runButton.setText(ACCIONES[funciones.getSelectedIndex()]));

        frame.pack();
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.getRootPane().setDefaultButton(runButton);
    }

    private JTabbedPane buildFunciones() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Descompilar", buildPanelDescompilar());
        tabs.addTab("Analizar", buildPanelAnalizar());
        tabs.addTab("Recompilar", buildPanelRecompilar());
        tabs.addTab("Nativos", buildPanelNativos());
        tabs.addTab("Nombres", buildPanelNombres());
        return tabs;
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        return panel;
    }

    private GridBagConstraints fila(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = y;
        return c;
    }

    private JLabel pista(String texto) {
        JLabel label = new JLabel(texto);
        label.setForeground(Color.GRAY);
        return label;
    }

    private JPanel buildPanelDescompilar() {
        JPanel panel = formPanel();
        GridBagConstraints c = fila(0);
        panel.add(new JLabel("APK:"), c);
        apkField = new JTextField(46);
        apkField.putClientProperty("JTextField.placeholderText", "Ruta del archivo .apk");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(apkField, c);
        JButton examinarApk = new JButton("Examinar…");
        examinarApk.addActionListener(e -> elegirApkDescompilar());
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(examinarApk, c);

        c = fila(1);
        c.gridx = 0;
        panel.add(new JLabel("Salida:"), c);
        outField = new JTextField(46);
        outField.putClientProperty("JTextField.placeholderText",
                "Directorio de salida (por defecto: aquadecomp-out/salida)");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(outField, c);
        JButton examinarSalida = new JButton("Examinar…");
        examinarSalida.addActionListener(e -> elegirSalida(outField));
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(examinarSalida, c);

        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        deobfCheck = new JCheckBox("Activar desofuscación", true);
        resourcesCheck = new JCheckBox("Incluir recursos", true);
        opts.add(deobfCheck);
        opts.add(resourcesCheck);
        c = fila(2);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(opts, c);

        return panel;
    }

    private JPanel buildPanelAnalizar() {
        JPanel panel = formPanel();
        GridBagConstraints c = fila(0);
        panel.add(new JLabel("APK:"), c);
        apkAnalizarField = new JTextField(46);
        apkAnalizarField.putClientProperty("JTextField.placeholderText", "Ruta del archivo .apk");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(apkAnalizarField, c);
        JButton examinar = new JButton("Examinar…");
        examinar.addActionListener(e -> elegirApk(apkAnalizarField));
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(examinar, c);

        c = fila(1);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(pista("Estructura, manifiesto, permisos y protección; no descompila."), c);

        return panel;
    }

    private JPanel buildPanelRecompilar() {
        JPanel panel = formPanel();
        GridBagConstraints c = fila(0);
        panel.add(new JLabel("Directorio:"), c);
        dirRecompilarField = new JTextField(46);
        dirRecompilarField.putClientProperty("JTextField.placeholderText",
                "Salida de una descompilación (contiene sources/)");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(dirRecompilarField, c);
        JButton examinarDir = new JButton("Examinar…");
        examinarDir.addActionListener(e -> elegirDirectorio(dirRecompilarField));
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(examinarDir, c);

        c = fila(1);
        c.gridx = 1;
        panel.add(pista("Recompila con javac + d8 y genera un APK firmado con los cambios."), c);

        c = fila(2);
        c.gridx = 0;
        panel.add(new JLabel("APK salida:"), c);
        salidaRecompilarField = new JTextField(46);
        salidaRecompilarField.putClientProperty("JTextField.placeholderText",
                "Por defecto: <directorio>/<original>-rebuilt.apk");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(salidaRecompilarField, c);

        c = fila(3);
        c.gridx = 0;
        panel.add(new JLabel("APK original:"), c);
        originalRecompilarField = new JTextField(46);
        originalRecompilarField.putClientProperty("JTextField.placeholderText",
                "Por defecto: el registrado en el informe de la descompilación");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(originalRecompilarField, c);
        JButton examinarOriginal = new JButton("Examinar…");
        examinarOriginal.addActionListener(e -> elegirApk(originalRecompilarField));
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(examinarOriginal, c);

        appCheck = new JCheckBox("Recompilar la aplicación completa", false);
        c = fila(4);
        c.gridx = 1;
        panel.add(appCheck, c);

        return panel;
    }

    private JPanel buildPanelNativos() {
        JPanel panel = formPanel();
        GridBagConstraints c = fila(0);
        panel.add(new JLabel("APK:"), c);
        apkNativosField = new JTextField(46);
        apkNativosField.putClientProperty("JTextField.placeholderText", "Ruta del archivo .apk");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(apkNativosField, c);
        JButton examinar = new JButton("Examinar…");
        examinar.addActionListener(e -> elegirApk(apkNativosField));
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(examinar, c);

        c = fila(1);
        c.gridx = 0;
        panel.add(new JLabel("Filtro:"), c);
        filtroNativosField = new JTextField(46);
        filtroNativosField.putClientProperty("JTextField.placeholderText",
                "Nombre de la biblioteca .so (opcional)");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(filtroNativosField, c);

        ghidraCheck = new JCheckBox("Solicitar desensamblado con Ghidra (si está instalado)", false);
        c = fila(2);
        c.gridx = 1;
        panel.add(ghidraCheck, c);

        return panel;
    }

    private JPanel buildPanelNombres() {
        JPanel panel = formPanel();
        GridBagConstraints c = fila(0);
        panel.add(new JLabel("Directorio:"), c);
        dirNombresField = new JTextField(46);
        dirNombresField.putClientProperty("JTextField.placeholderText",
                "Fuentes .java de una descompilación");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(dirNombresField, c);
        JButton examinarDir = new JButton("Examinar…");
        examinarDir.addActionListener(e -> elegirDirectorio(dirNombresField));
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(examinarDir, c);

        c = fila(1);
        c.gridx = 1;
        panel.add(pista("Solo sugerencias: no modifica ningún archivo."), c);

        c = fila(2);
        c.gridx = 0;
        panel.add(new JLabel("JSON en:"), c);
        outNombresField = new JTextField(46);
        outNombresField.putClientProperty("JTextField.placeholderText",
                "Archivo para el informe JSON (opcional)");
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(outNombresField, c);

        return panel;
    }

    private JPanel buildCenterPanel() {
        reportArea = new JTextArea();
        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        reportArea.setText("  Selecciona una función y pulsa el botón para empezar.\n");

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

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

        runButton = new JButton(ACCIONES[0]);
        runButton.putClientProperty("JButton.buttonType", "primary");
        runButton.addActionListener(e -> ejecutarAccion());

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

    private void elegirApkDescompilar() {
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

    private void elegirApk(JTextField destino) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar APK");
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            destino.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void elegirSalida(JTextField destino) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Directorio de salida");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            destino.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void elegirDirectorio(JTextField destino) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar directorio");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            destino.setText(fc.getSelectedFile().getAbsolutePath());
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

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(frame, mensaje,
                "Aquadecomp", JOptionPane.WARNING_MESSAGE);
    }

    private void ejecutarAccion() {
        switch (funciones.getSelectedIndex()) {
            case 0 -> ejecutarDescompilar();
            case 1 -> ejecutarAnalizar();
            case 2 -> ejecutarRecompilar();
            case 3 -> ejecutarNativos();
            default -> ejecutarNombres();
        }
    }

    private void ejecutarDescompilar() {
        String apkTxt = apkField.getText().trim();
        if (apkTxt.isEmpty()) {
            aviso("Selecciona un APK primero.");
            return;
        }
        Path apk = Path.of(apkTxt);
        Path salida = outField.getText().isBlank()
                ? Path.of("aquadecomp-out", "salida")
                : Path.of(outField.getText().trim());
        boolean deobf = deobfCheck.isSelected();
        boolean recursos = resourcesCheck.isSelected();

        lanzarTarea("Descompilando…", listener -> {
            PipelineResult result = new Pipeline().run(
                    new Pipeline.Options(apk, salida, deobf, recursos, 0), listener);
            return new ResultadoTarea(buildReportText(result),
                    result.success() ? Path.of(result.outputDir()) : null,
                    result.success() ? null : result.errorMessage());
        });
    }

    private void ejecutarAnalizar() {
        String apkTxt = apkAnalizarField.getText().trim();
        if (apkTxt.isEmpty()) {
            aviso("Selecciona un APK primero.");
            return;
        }
        Path apk = Path.of(apkTxt);
        if (!Files.exists(apk)) {
            aviso("El archivo no existe: " + apk);
            return;
        }

        lanzarTarea("Analizando…", listener -> {
            ApkInspector inspector = new ApkInspector();
            InputNormalizer.NormalizedInput input = new InputNormalizer().normalize(apk);
            try {
                ApkStructure estructura = inspector.inspect(input.analysisTarget());
                ManifestInfo manifiesto = inspector.readManifest(input.analysisTarget());
                return new ResultadoTarea(
                        textoInspeccion(apk, input.type().label, input.isContainer(),
                                estructura, manifiesto),
                        null, null);
            } finally {
                if (input.primaryApk() != null) {
                    try {
                        Files.deleteIfExists(input.primaryApk());
                    } catch (IOException ignored) {
                    }
                }
            }
        });
    }

    private void ejecutarRecompilar() {
        String dirTxt = dirRecompilarField.getText().trim();
        if (dirTxt.isEmpty()) {
            aviso("Indica el directorio de una descompilación.");
            return;
        }
        Path dir = Path.of(dirTxt);
        Path salida = salidaRecompilarField.getText().isBlank()
                ? null : Path.of(salidaRecompilarField.getText().trim());
        Path original = originalRecompilarField.getText().isBlank()
                ? null : Path.of(originalRecompilarField.getText().trim());
        boolean app = appCheck.isSelected();

        lanzarTarea("Recompilando…", listener -> {
            RebuildPipeline.Options opts = new RebuildPipeline.Options(
                    dir, original, salida, null, List.of(), app, 21, null);
            RebuildReport report = new RebuildPipeline().run(opts, listener);
            Path carpeta = null;
            if (report.success() && report.outputApk() != null) {
                carpeta = Path.of(report.outputApk()).toAbsolutePath().getParent();
            }
            String error = null;
            if (!report.success()) {
                error = report.errorMessage() != null
                        ? report.errorMessage() : "falló la recompilación";
            }
            return new ResultadoTarea(report.consoleSummary(), carpeta, error);
        });
    }

    private void ejecutarNativos() {
        String apkTxt = apkNativosField.getText().trim();
        if (apkTxt.isEmpty()) {
            aviso("Selecciona un APK primero.");
            return;
        }
        Path apk = Path.of(apkTxt);
        if (!Files.exists(apk)) {
            aviso("El archivo no existe: " + apk);
            return;
        }
        String filtroTxt = filtroNativosField.getText().trim();
        String filtro = filtroTxt.isEmpty() ? null : filtroTxt;
        boolean ghidra = ghidraCheck.isSelected();

        lanzarTarea("Analizando bibliotecas…", listener -> {
            NativeAnalyzer.Report report = NativeAnalyzer.analyze(apk, filtro, ghidra, null, null);
            String error = ghidra && !report.ghidra().available()
                    ? report.ghidra().message() : null;
            return new ResultadoTarea(textoNativos(apk, report, 10), null, error);
        });
    }

    private void ejecutarNombres() {
        String dirTxt = dirNombresField.getText().trim();
        if (dirTxt.isEmpty()) {
            aviso("Indica el directorio con los fuentes .java.");
            return;
        }
        Path dir = Path.of(dirTxt);
        String outTxt = outNombresField.getText().trim();
        Path out = outTxt.isEmpty() ? null : Path.of(outTxt);

        lanzarTarea("Sugiriendo nombres…", listener -> {
            NameReport report = NameInferer.infer(dir);
            StringBuilder sb = new StringBuilder(textoNombres(report, 40));
            if (out != null) {
                Files.writeString(out, report.toJson(), StandardCharsets.UTF_8);
                sb.append("\nInforme JSON: ").append(out.toAbsolutePath()).append('\n');
            }
            return new ResultadoTarea(sb.toString(), null, null);
        });
    }

    private void lanzarTarea(String inicio, Tarea tarea) {
        runButton.setEnabled(false);
        logArea.setText("");
        reportArea.setText("");
        progressBar.setValue(0);
        statusLabel.setText(inicio);

        SwingWorker<ResultadoTarea, String> worker = new SwingWorker<>() {
            @Override
            protected ResultadoTarea doInBackground() throws Exception {
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
                return tarea.ejecutar(listener);
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
                    ResultadoTarea r = get();
                    reportArea.setText(r.texto());
                    reportArea.setCaretPosition(0);
                    lastOutputDir = r.carpeta();
                    openFolderButton.setEnabled(r.carpeta() != null);
                    if (r.error() == null) {
                        statusLabel.setText("Completado");
                        progressBar.setValue(100);
                    } else {
                        statusLabel.setText("Error: " + r.error());
                        JOptionPane.showMessageDialog(frame, "Error: " + r.error(),
                                "Aquadecomp", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrumpido");
                } catch (ExecutionException e) {
                    Throwable causa = e.getCause() != null ? e.getCause() : e;
                    String msg = causa.getMessage() != null
                            ? causa.getMessage() : causa.toString();
                    statusLabel.setText("Error: " + msg);
                    reportArea.setText("ERROR: " + msg);
                    JOptionPane.showMessageDialog(frame, "Error: " + msg,
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

    static String textoInspeccion(Path apk, String formato, boolean contenedor,
                                  ApkStructure s, ManifestInfo m) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Analizar APK ===\n");
        sb.append("APK:        ").append(apk).append('\n');
        sb.append("Formato:    ").append(formato)
                .append(contenedor ? " (analizado sobre su APK interno)" : "").append('\n');
        sb.append(String.format("Tamaño:     %.2f MB%n", s.sizeBytes() / 1024.0 / 1024.0));
        sb.append("Entradas:   ").append(s.entryCount()).append('\n');
        sb.append("DEX:        ").append(s.dexFiles()).append('\n');
        sb.append("Libs:       ").append(s.nativeLibs().size()).append(" .so\n");
        sb.append("Assets:     ").append(s.assetEntries().size()).append('\n');
        sb.append("\n-- Manifiesto --\n");
        if (m.parsedOk()) {
            sb.append("Paquete:    ").append(m.packageName()).append('\n');
            sb.append("Versión:    ").append(m.versionName())
                    .append(" (").append(m.versionCode()).append(")\n");
            sb.append("Principal:  ").append(m.mainActivity()).append('\n');
            sb.append("Permisos (").append(m.permissions().size()).append("):\n");
            m.permissions().forEach(p -> sb.append("   - ").append(p).append('\n'));
        } else {
            sb.append("Error: ").append(m.parseError()).append('\n');
        }
        sb.append("\n-- Protección --\n");
        if (s.detectedPackers().isEmpty()) {
            sb.append("Sin packers conocidos detectados\n");
        } else {
            s.detectedPackers().forEach(p -> sb.append("   Aviso: ").append(p).append('\n'));
        }
        if (!s.warnings().isEmpty()) {
            sb.append("-- Avisos --\n");
            s.warnings().forEach(w -> sb.append("   · ").append(w).append('\n'));
        }
        return sb.toString();
    }

    static String textoNativos(Path apk, NativeAnalyzer.Report report, int limite) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Bibliotecas nativas ===\n");
        sb.append("APK:         ").append(apk).append('\n');
        sb.append("Bibliotecas: ").append(report.libraries()).append('\n');
        for (NativeAnalyzer.Library lib : report.items()) {
            sb.append('\n').append(lib.entry()).append('\n');
            if (lib.error() != null) {
                sb.append("  Error: ").append(lib.error()).append('\n');
                continue;
            }
            var elf = lib.elf();
            sb.append("  ABI:          ").append(elf.abi()).append(" (").append(elf.bits())
                    .append(" bits, ").append(elf.littleEndian() ? "little" : "big")
                    .append(" endian)\n");
            sb.append("  Arquitectura: ").append(elf.architecture())
                    .append("  e_machine=").append(elf.machine()).append('\n');
            sb.append("  Estado:       ").append(elf.stripped()
                    ? "sin symtab (stripped)" : "con symtab").append('\n');
            sb.append("  Secciones:    ").append(elf.sections().size()).append('\n');
            sb.append("  Símbolos:     ").append(elf.symbols()).append(" (exports ")
                    .append(elf.exports().size()).append(", imports ")
                    .append(elf.imports()).append(")\n");
            if (elf.soname() != null) {
                sb.append("  SONAME:       ").append(elf.soname()).append('\n');
            }
            if (!elf.needed().isEmpty()) {
                sb.append("  Necesita:     ").append(String.join(", ", elf.needed())).append('\n');
            }
            if (!elf.exports().isEmpty()) {
                int mostrados = Math.min(limite, elf.exports().size());
                sb.append("  Primeros exports (").append(mostrados).append('/')
                        .append(elf.exports().size()).append("):\n");
                for (int i = 0; i < mostrados; i++) {
                    sb.append("    - ").append(elf.exports().get(i)).append('\n');
                }
            }
        }
        if (!report.warnings().isEmpty()) {
            sb.append("\nAvisos:\n");
            report.warnings().forEach(w -> sb.append("  - ").append(w).append('\n'));
        }
        var ghidra = report.ghidra();
        sb.append("\n-- Ghidra --\n").append(ghidra.message()).append('\n');
        if (ghidra.outputFile() != null) {
            sb.append("Salida:  ").append(ghidra.outputFile().toAbsolutePath()).append('\n');
            sb.append("Funciones descompiladas: ").append(ghidra.functions()).append('\n');
        }
        return sb.toString();
    }

    static String textoNombres(NameReport report, int limite) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Inferencia de nombres ===\n");
        sb.append("Directorio:   ").append(report.sourceDir()).append('\n');
        sb.append("Archivos:     ").append(report.filesScanned()).append('\n');
        sb.append("Sugerencias:  ").append(report.suggestions().size()).append('\n');
        int mostradas = 0;
        for (var s : report.suggestions()) {
            if (mostradas >= limite) {
                break;
            }
            sb.append("  [").append(s.kind()).append("] ").append(s.current())
                    .append(" -> ").append(s.suggested()).append("  (confianza ")
                    .append(s.confidence()).append(", ").append(s.file()).append(")\n");
            for (String senal : s.signals()) {
                sb.append("      señal: ").append(senal).append('\n');
            }
            mostradas++;
        }
        if (report.suggestions().size() > mostradas) {
            sb.append("  ... y ").append(report.suggestions().size() - mostradas)
                    .append(" más (usa el informe JSON)\n");
        }
        if (report.suggestions().isEmpty()) {
            sb.append("No se detectaron identificadores que parezcan ofuscados.\n");
        }
        for (String nota : report.notes()) {
            sb.append("Nota: ").append(nota).append('\n');
        }
        return sb.toString();
    }
}
