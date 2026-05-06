package com.biometrics.ui;

import com.biometrics.model.RecognitionResult;
import com.biometrics.service.*;
import com.biometrics.util.ImageUtils;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.opencv.opencv_core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MainWindow
 *
 * Interface gráfica principal do sistema de biometria facial.
 *
 * Layout:
 *   ┌─────────────────────────────────────┐
 *   │          VÍDEO DA CÂMERA            │
 *   │   (640x480 - detecção em tempo real)│
 *   ├─────────────────────────────────────┤
 *   │  STATUS: Aguardando...              │
 *   ├────────────┬────────┬───────────────┤
 *   │ [Cadastrar]│[Treinar]│ [Reconhecer] │
 *   └────────────┴────────┴───────────────┘
 *
 * Modo de operação:
 *  - DETECTING: detecção passiva em tempo real
 *  - CAPTURING: captura imagens para cadastro
 *  - RECOGNIZING: identifica rostos capturados
 */
public class MainWindow extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);

    // ─── Serviços ─────────────────────────────────────────────────
    private final CameraService cameraService = new CameraService();
    private final FaceDetectorService detectorService = new FaceDetectorService();
    private final FaceRecognizerService recognizerService = new FaceRecognizerService();
    private final DatasetManager datasetManager = new DatasetManager();

    // ─── Constantes ───────────────────────────────────────────────
    private static final String MODEL_PATH = "models/modelo.yml";
    private static final int FPS = 30;

    // ─── Estado da aplicação ──────────────────────────────────────
    private enum Mode { DETECTING, CAPTURING, RECOGNIZING }
    private volatile Mode currentMode = Mode.DETECTING;

    private String captureUserName = null;
    private int captureCount = 0;
    private Map<Integer, String> labelMap;

    // ─── Componentes Swing ────────────────────────────────────────
    private JLabel videoLabel;
    private JLabel statusLabel;
    private JProgressBar captureProgress;
    private JButton btnCapture;
    private JButton btnTrain;
    private JButton btnRecognize;

    // ─── Thread de captura ────────────────────────────────────────
    private ScheduledExecutorService executor;

    // ─── Construtor ───────────────────────────────────────────────

    public MainWindow() {
        initUI();
        initServices();
        startVideoLoop();
    }

    // ─── Inicialização da UI ──────────────────────────────────────

    private void initUI() {
        setTitle("Sistema de Biometria Facial - Java + OpenCV");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);

        // Fecha câmera antes de encerrar
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
                System.exit(0);
            }
        });

        // ── Painel principal ─────────────────────────────────────
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(new Color(18, 18, 24));

        // ── Vídeo ─────────────────────────────────────────────────
        videoLabel = new JLabel();
        videoLabel.setPreferredSize(new Dimension(640, 480));
        videoLabel.setHorizontalAlignment(JLabel.CENTER);
        videoLabel.setBackground(Color.BLACK);
        videoLabel.setOpaque(true);
        videoLabel.setBorder(BorderFactory.createLineBorder(new Color(0, 200, 130), 2));

        JPanel videoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        videoPanel.setBackground(new Color(18, 18, 24));
        videoPanel.setBorder(new EmptyBorder(12, 12, 8, 12));
        videoPanel.add(videoLabel);

        // ── Status bar ────────────────────────────────────────────
        statusLabel = new JLabel("▶ Inicializando...");
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        statusLabel.setForeground(new Color(0, 220, 150));
        statusLabel.setBorder(new EmptyBorder(6, 16, 6, 16));

        captureProgress = new JProgressBar(0, DatasetManager.IMAGES_PER_USER);
        captureProgress.setStringPainted(true);
        captureProgress.setForeground(new Color(0, 180, 255));
        captureProgress.setBackground(new Color(40, 40, 50));
        captureProgress.setVisible(false);
        captureProgress.setPreferredSize(new Dimension(200, 18));

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(28, 28, 36));
        statusPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(50, 50, 60)));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(captureProgress, BorderLayout.EAST);

        // ── Botões ────────────────────────────────────────────────
        btnCapture   = createButton("📷  Cadastrar Usuário",  new Color(0, 140, 200));
        btnTrain     = createButton("🧠  Treinar Modelo",     new Color(120, 60, 200));
        btnRecognize = createButton("🔍  Reconhecer Rosto",   new Color(0, 160, 100));

        btnCapture.addActionListener(e -> startCapture());
        btnTrain.addActionListener(e -> trainModel());
        btnRecognize.addActionListener(e -> toggleRecognition());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.setBackground(new Color(18, 18, 24));
        buttonPanel.add(btnCapture);
        buttonPanel.add(btnTrain);
        buttonPanel.add(btnRecognize);

        // ── Montagem ──────────────────────────────────────────────
        mainPanel.add(videoPanel,   BorderLayout.CENTER);
        mainPanel.add(statusPanel,  BorderLayout.NORTH);
        mainPanel.add(buttonPanel,  BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
    }

    private JButton createButton(String text, Color baseColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setBackground(baseColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(190, 38));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(baseColor.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(baseColor);
            }
        });

        return btn;
    }

    // ─── Inicialização dos serviços ───────────────────────────────

    private void initServices() {
        try {
            detectorService.initialize();
            recognizerService.initialize();

            // Tenta carregar o modelo salvo (se existir)
            try {
                recognizerService.loadModel(MODEL_PATH);
                labelMap = datasetManager.loadLabelMap();
                setStatus("✅ Modelo carregado! Usuários: " + labelMap.values(), new Color(0, 220, 130));
            } catch (Exception e) {
                setStatus("⚠ Nenhum modelo encontrado. Cadastre usuários primeiro.", Color.ORANGE);
            }

        } catch (IOException e) {
            setStatus("❌ Erro ao inicializar: " + e.getMessage(), Color.RED);
            log.error("Falha na inicialização dos serviços", e);
        }
    }

    // ─── Loop de vídeo ────────────────────────────────────────────

    private void startVideoLoop() {
        try {
            cameraService.start();
        } catch (FrameGrabber.Exception e) {
            setStatus("❌ Câmera não encontrada: " + e.getMessage(), Color.RED);
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::processFrame, 0, 1000 / FPS, TimeUnit.MILLISECONDS);
    }

    /**
     * Processado a cada frame (30x por segundo).
     * Executa fora da EDT do Swing para não travar a UI.
     */
    private void processFrame() {
        try {
            Mat frame = cameraService.captureFrame();
            if (frame == null) return;

            // Detecta rostos em todo frame
            RectVector faces = detectorService.detect(frame);

            // Processa de acordo com o modo atual
            switch (currentMode) {
                case CAPTURING  -> handleCapturing(frame, faces);
                case RECOGNIZING -> handleRecognizing(frame, faces);
                default         -> handleDetecting(frame, faces);
            }

            // Exibe o frame processado na interface
            displayFrame(frame);
            frame.release();

        } catch (Exception e) {
            log.error("Erro no processamento do frame", e);
        }
    }

    private void handleDetecting(Mat frame, RectVector faces) {
        for (int i = 0; i < faces.size(); i++) {
            ImageUtils.drawFaceBox(frame, faces.get(i),
                    ImageUtils.COLOR_DETECTING, "Rosto Detectado");
        }
    }

    private void handleCapturing(Mat frame, RectVector faces) {
        if (faces.size() == 0) return;

        Rect face = faces.get(0); // Usa o primeiro rosto detectado
        ImageUtils.drawFaceBox(frame, face, ImageUtils.COLOR_CAPTURING,
                "Capturando... " + captureCount + "/" + DatasetManager.IMAGES_PER_USER);

        // Captura com intervalo (a cada 3 frames para variar um pouco o rosto)
        if (captureCount < DatasetManager.IMAGES_PER_USER) {
            Mat faceROI = detectorService.extractFaceROI(frame, face);
            Mat preprocessed = ImageUtils.preprocess(faceROI);
            datasetManager.saveFaceImage(captureUserName, preprocessed, captureCount + 1);
            faceROI.release();
            preprocessed.release();

            captureCount++;
            final int count = captureCount;

            SwingUtilities.invokeLater(() -> {
                captureProgress.setValue(count);
                captureProgress.setString(count + "/" + DatasetManager.IMAGES_PER_USER);
            });
        } else {
            // Captura concluída
            finishCapture();
        }
    }

    private void handleRecognizing(Mat frame, RectVector faces) {
        if (labelMap == null) return;

        for (int i = 0; i < faces.size(); i++) {
            Rect face = faces.get(i);
            Mat faceROI = detectorService.extractFaceROI(frame, face);
            Mat preprocessed = ImageUtils.preprocess(faceROI);

            RecognitionResult result = recognizerService.recognize(preprocessed, labelMap);

            Scalar color = result.isRecognized()
                    ? ImageUtils.COLOR_RECOGNIZED
                    : ImageUtils.COLOR_UNKNOWN;

            ImageUtils.drawFaceBox(frame, face, color, result.toDisplayString());
            faceROI.release();
            preprocessed.release();
        }
    }

    // ─── Ações dos botões ─────────────────────────────────────────

    private void startCapture() {
        String name = JOptionPane.showInputDialog(this,
                "Digite o nome do usuário:",
                "Cadastrar Usuário",
                JOptionPane.QUESTION_MESSAGE);

        if (name == null || name.trim().isEmpty()) return;

        captureUserName = name.trim();
        captureCount = 0;
        currentMode = Mode.CAPTURING;

        captureProgress.setValue(0);
        captureProgress.setMaximum(DatasetManager.IMAGES_PER_USER);
        captureProgress.setVisible(true);
        setStatus("📷 Capturando imagens de: " + captureUserName + " — posicione o rosto na câmera",
                new Color(255, 165, 0));
    }

    private void finishCapture() {
        currentMode = Mode.DETECTING;

        try {
            datasetManager.registerUser(captureUserName);
            SwingUtilities.invokeLater(() -> {
                captureProgress.setVisible(false);
                setStatus("✅ Cadastro de '" + captureUserName + "' concluído! Agora clique em Treinar.",
                        new Color(0, 220, 130));
                JOptionPane.showMessageDialog(this,
                        captureUserName + " cadastrado(a) com sucesso!\n" +
                                "Clique em 'Treinar Modelo' para atualizar o reconhecedor.",
                        "Cadastro Concluído", JOptionPane.INFORMATION_MESSAGE);
            });
        } catch (IOException e) {
            log.error("Erro ao registrar usuário", e);
            setStatus("❌ Erro ao salvar usuário: " + e.getMessage(), Color.RED);
        }
    }

    private void trainModel() {
        setStatus("🧠 Treinando modelo... aguarde.", new Color(180, 130, 255));
        btnTrain.setEnabled(false);

        // Executa o treinamento em thread separada para não travar a UI
        new Thread(() -> {
            try {
                DatasetManager.DatasetBundle bundle = datasetManager.loadDataset();

                if (bundle.images.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("⚠ Nenhuma imagem no dataset. Cadastre usuários primeiro.", Color.ORANGE);
                        btnTrain.setEnabled(true);
                        JOptionPane.showMessageDialog(this,
                                "Nenhuma imagem encontrada no dataset.\nCadastre pelo menos um usuário primeiro.",
                                "Aviso", JOptionPane.WARNING_MESSAGE);
                    });
                    return;
                }

                recognizerService.train(bundle.images, bundle.labelMap);
                recognizerService.saveModel(MODEL_PATH);
                labelMap = bundle.labelMap;

                SwingUtilities.invokeLater(() -> {
                    btnTrain.setEnabled(true);
                    setStatus("✅ Modelo treinado e salvo! Usuários: " + labelMap.values(),
                            new Color(0, 220, 130));
                    JOptionPane.showMessageDialog(this,
                            "Modelo treinado com sucesso!\n" +
                                    "Usuários: " + labelMap.values() + "\n" +
                                    "Imagens: " + bundle.images.size(),
                            "Treinamento Concluído", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception e) {
                log.error("Erro no treinamento", e);
                SwingUtilities.invokeLater(() -> {
                    btnTrain.setEnabled(true);
                    setStatus("❌ Erro no treinamento: " + e.getMessage(), Color.RED);
                });
            }
        }, "training-thread").start();
    }

    private void toggleRecognition() {
        if (currentMode == Mode.RECOGNIZING) {
            currentMode = Mode.DETECTING;
            btnRecognize.setText("🔍  Reconhecer Rosto");
            setStatus("▶ Detecção ativa. Pronto.", new Color(0, 200, 130));
        } else {
            if (!recognizerService.isTrained()) {
                JOptionPane.showMessageDialog(this,
                        "Nenhum modelo treinado encontrado.\nCadastre usuários e clique em 'Treinar Modelo'.",
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            currentMode = Mode.RECOGNIZING;
            btnRecognize.setText("⏹  Parar Reconhecimento");
            setStatus("🔍 Reconhecimento ativo — aponte a câmera para um rosto cadastrado.",
                    new Color(100, 200, 255));
        }
    }

    // ─── Helpers de UI ────────────────────────────────────────────

    private void displayFrame(Mat frame) {
        BufferedImage image = ImageUtils.matToBufferedImage(frame);
        if (image == null) return;

        ImageIcon icon = new ImageIcon(image);
        SwingUtilities.invokeLater(() -> videoLabel.setIcon(icon));
    }

    private void setStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("  " + text);
            statusLabel.setForeground(color);
        });
    }

    // ─── Encerramento ─────────────────────────────────────────────

    private void shutdown() {
        log.info("Encerrando aplicação...");
        if (executor != null) {
            executor.shutdownNow();
        }
        cameraService.stop();
    }
}