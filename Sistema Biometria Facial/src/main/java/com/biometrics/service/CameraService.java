package com.biometrics.service;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CameraService
 *
 * Responsabilidade: gerenciar o ciclo de vida da webcam.
 * - Abre/fecha a câmera
 * - Captura frames em tempo real
 * - Converte Frame (JavaCV) → Mat (OpenCV)
 *
 * Por que JavaCV?
 *   O OpenCV puro em Java requer carregar bibliotecas nativas (.dll/.so/.dylib)
 *   manualmente. O JavaCV abstrai isso e oferece FrameGrabber, que funciona
 *   em Windows, macOS e Linux sem configuração extra.
 */
public class CameraService {

    private static final Logger log = LoggerFactory.getLogger(CameraService.class);

    /** Índice da câmera (0 = câmera padrão do sistema) */
    private static final int CAMERA_INDEX = 0;

    /** Largura desejada do frame capturado */
    private static final int FRAME_WIDTH = 640;

    /** Altura desejada do frame capturado */
    private static final int FRAME_HEIGHT = 480;

    /** Objeto principal de captura de frames da webcam */
    private OpenCVFrameGrabber grabber;

    /** Converte entre o formato Frame (JavaCV) e Mat (OpenCV) */
    private final OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();

    /** Indica se a câmera está ativa */
    private boolean running = false;

    /**
     * Inicializa e abre a webcam.
     *
     * @throws FrameGrabber.Exception se a câmera não puder ser acessada
     */
    public void start() throws FrameGrabber.Exception {
        if (running) {
            log.warn("Câmera já está em execução.");
            return;
        }

        log.info("Abrindo câmera no índice {}...", CAMERA_INDEX);
        grabber = new OpenCVFrameGrabber(CAMERA_INDEX);
        grabber.setImageWidth(FRAME_WIDTH);
        grabber.setImageHeight(FRAME_HEIGHT);
        grabber.start();

        running = true;
        log.info("Câmera aberta com sucesso. Resolução: {}x{}", FRAME_WIDTH, FRAME_HEIGHT);
    }

    /**
     * Captura o próximo frame da webcam e retorna como Mat do OpenCV.
     *
     * @return Mat com o frame atual, ou null se não houver frame disponível
     * @throws FrameGrabber.Exception em caso de erro de captura
     */
    public Mat captureFrame() throws FrameGrabber.Exception {
        if (!running || grabber == null) {
            throw new IllegalStateException("Câmera não iniciada. Chame start() primeiro.");
        }

        Frame frame = grabber.grab();
        if (frame == null || frame.image == null) {
            return null;
        }

        // Converte Frame → Mat (formato usado pelas funções OpenCV)
        return converter.convert(frame);
    }

    /**
     * Encerra a captura e libera os recursos da câmera.
     */
    public void stop() {
        if (!running) return;

        try {
            log.info("Encerrando câmera...");
            grabber.stop();
            grabber.release();
            running = false;
            log.info("Câmera encerrada.");
        } catch (FrameGrabber.Exception e) {
            log.error("Erro ao encerrar a câmera: {}", e.getMessage());
        }
    }

    /** @return true se a câmera está ativa */
    public boolean isRunning() {
        return running;
    }
}