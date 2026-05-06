package com.biometrics.util;

import org.bytedeco.opencv.opencv_core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * ImageUtils
 *
 * Utilitários para pré-processamento de imagens e conversão entre
 * os formatos Mat (OpenCV) e BufferedImage (Java Swing).
 *
 * Por que pré-processar?
 *   O LBPH é sensível a variações de iluminação e pose.
 *   Aplicar equalização de histograma e normalização melhora
 *   significativamente a taxa de acerto.
 */
public class ImageUtils {

    private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

    /**
     * Aplica pré-processamento na imagem do rosto para melhorar o reconhecimento.
     *
     * Pipeline de pré-processamento:
     *   1. Garante escala de cinza
     *   2. Equalização de histograma (contraste)
     *   3. Filtro Gaussiano (suaviza ruído)
     *
     * @param face Mat do rosto (cinza ou colorido)
     * @return Mat pré-processado em escala de cinza
     */
    public static Mat preprocess(Mat face) {
        Mat result = new Mat();

        // 1. Garantir escala de cinza
        if (face.channels() == 3) {
            cvtColor(face, result, COLOR_BGR2GRAY);
        } else {
            result = face.clone();
        }

        // 2. Equalização de histograma
        //    Redistribui os pixels para usar toda a faixa de 0-255
        Mat equalized = new Mat();
        equalizeHist(result, equalized);

        // 3. Suavização Gaussiana
        //    Remove ruído que poderia atrapalhar a extração LBP
        Mat smoothed = new Mat();
        GaussianBlur(equalized, smoothed, new Size(3, 3), 0);

        result.release();
        equalized.release();

        return smoothed;
    }

    /**
     * Converte Mat (OpenCV BGR) para BufferedImage (Java Swing).
     *
     * Esta conversão é necessária para exibir o vídeo em um JLabel no Swing.
     * O processo:
     *   Mat BGR → Mat RGB → BufferedImage TYPE_3BYTE_BGR
     *
     * @param mat Mat colorido (BGR)
     * @return BufferedImage para uso no Swing
     */
    public static BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }

        int type;
        Mat converted = new Mat();

        if (mat.channels() == 1) {
            // Grayscale
            cvtColor(mat, converted, COLOR_GRAY2BGR);
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else {
            // OpenCV usa BGR, Java usa RGB/BGR
            // BufferedImage.TYPE_3BYTE_BGR aceita BGR diretamente
            converted = mat.clone();
            type = BufferedImage.TYPE_3BYTE_BGR;
        }

        int width = converted.cols();
        int height = converted.rows();
        int channels = converted.channels();

        byte[] data = new byte[width * height * channels];
        converted.data().get(data);

        BufferedImage image = new BufferedImage(width, height, type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(data, 0, targetPixels, 0, data.length);

        converted.release();
        return image;
    }

    /**
     * Desenha um retângulo colorido com bordas suaves no rosto detectado.
     *
     * @param frame Frame onde desenhar (modificado in-place)
     * @param rect  Retângulo do rosto
     * @param color Cor do retângulo (BGR)
     * @param label Texto a exibir acima do retângulo (pode ser null)
     */
    public static void drawFaceBox(Mat frame, Rect rect, Scalar color, String label) {
        // Retângulo principal
        rectangle(frame, rect, color, 2, LINE_8, 0);

        // Decoração: pequenos quadrados nos cantos (estilo moderno)
        int cornerLen = 15;
        int x = rect.x(), y = rect.y(), w = rect.width(), h = rect.height();
        int thick = 3;

        // Canto superior esquerdo
        line(frame, new Point(x, y), new Point(x + cornerLen, y), color, thick, LINE_8, 0);
        line(frame, new Point(x, y), new Point(x, y + cornerLen), color, thick, LINE_8, 0);

        // Canto superior direito
        line(frame, new Point(x + w - cornerLen, y), new Point(x + w, y), color, thick, LINE_8, 0);
        line(frame, new Point(x + w, y), new Point(x + w, y + cornerLen), color, thick, LINE_8, 0);

        // Canto inferior esquerdo
        line(frame, new Point(x, y + h - cornerLen), new Point(x, y + h), color, thick, LINE_8, 0);
        line(frame, new Point(x, y + h), new Point(x + cornerLen, y + h), color, thick, LINE_8, 0);

        // Canto inferior direito
        line(frame, new Point(x + w - cornerLen, y + h), new Point(x + w, y + h), color, thick, LINE_8, 0);
        line(frame, new Point(x + w, y + h - cornerLen), new Point(x + w, y + h), color, thick, LINE_8, 0);

        // Label acima do retângulo
        if (label != null && !label.isEmpty()) {
            int fontFace = FONT_HERSHEY_SIMPLEX;
            double fontScale = 0.6;
            int[] baseline = {0};

            Size textSize = getTextSize(label, fontFace, fontScale, 1, baseline);

            // Fundo do texto
            int textX = x;
            int textY = Math.max(y - 10, textSize.height());
            Rect bgRect = new Rect(textX, textY - textSize.height() - 4,
                    textSize.width() + 8, textSize.height() + 8);
            rectangle(frame, bgRect, color, -1, LINE_8, 0);

            // Texto branco sobre o fundo colorido
            putText(frame, label,
                    new Point(textX + 4, textY),
                    fontFace, fontScale,
                    new Scalar(255, 255, 255, 0), 1, LINE_8, false);
        }
    }

    /**
     * Cores para os estados de reconhecimento (BGR).
     */
    public static final Scalar COLOR_DETECTING  = new Scalar(0, 255, 255, 0);  // Amarelo
    public static final Scalar COLOR_RECOGNIZED = new Scalar(0, 255, 0, 0);    // Verde
    public static final Scalar COLOR_UNKNOWN    = new Scalar(0, 0, 255, 0);    // Vermelho
    public static final Scalar COLOR_CAPTURING  = new Scalar(255, 165, 0, 0);  // Laranja
}