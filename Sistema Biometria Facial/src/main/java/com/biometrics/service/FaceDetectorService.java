package com.biometrics.service;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_core.*;

/**
 * FaceDetectorService
 *
 * Responsabilidade: detectar rostos em um frame (Mat) usando Haar Cascade.
 *
 * O que é Haar Cascade?
 *   É um método clássico de visão computacional proposto por Viola e Jones (2001).
 *   Usa um classificador treinado com milhares de imagens positivas (com rostos)
 *   e negativas (sem rostos). O arquivo XML define as "cascades" (etapas de filtro).
 *
 * Por que funciona bem?
 *   - Extremamente rápido (tempo real em CPU comum)
 *   - Boa precisão para detecção frontal de rostos
 *   - Já vem embutido no OpenCV
 *
 * Limitações:
 *   - Menor precisão para rostos de perfil
 *   - Sensível a iluminação ruim
 *   - Pode gerar falsos positivos em objetos com padrão facial
 */
public class FaceDetectorService {

    private static final Logger log = LoggerFactory.getLogger(FaceDetectorService.class);

    /**
     * Arquivo XML com o modelo Haar Cascade para detecção frontal de rostos.
     * Este arquivo está incluso nas dependências do JavaCV.
     */
    private static final String CASCADE_RESOURCE = "haarcascade_frontalface_default.xml";

    /** Classificador carregado com o modelo Haar Cascade */
    private CascadeClassifier classifier;

    /**
     * Inicializa o detector, carregando o modelo Haar Cascade do classpath.
     *
     * @throws IOException se o arquivo do modelo não for encontrado
     */
    public void initialize() throws IOException {
        log.info("Inicializando detector de rostos (Haar Cascade)...");

        // O arquivo XML está dentro do JAR do OpenCV (via JavaCV).
        // Precisamos extraí-lo para um arquivo temporário para que o
        // CascadeClassifier nativo do OpenCV possa ler.
        Path tempFile = extractCascadeToTemp();

        classifier = new CascadeClassifier(tempFile.toAbsolutePath().toString());

        if (classifier.empty()) {
            throw new IOException("Falha ao carregar o modelo Haar Cascade: " + CASCADE_RESOURCE);
        }

        log.info("Detector de rostos inicializado com sucesso.");
    }

    /**
     * Detecta rostos em um frame colorido.
     *
     * O processo interno:
     *  1. Converte para escala de cinza (o Haar Cascade opera em grayscale)
     *  2. Equaliza o histograma (melhora contraste → melhor detecção)
     *  3. Executa detectMultiScale (o coração do algoritmo)
     *
     * @param colorFrame Frame colorido da câmera (BGR)
     * @return RectVector com os retângulos dos rostos detectados
     */
    public RectVector detect(Mat colorFrame) {
        if (classifier == null || classifier.empty()) {
            throw new IllegalStateException("Detector não inicializado. Chame initialize() primeiro.");
        }

        // 1. Converter para escala de cinza
        Mat gray = new Mat();
        cvtColor(colorFrame, gray, COLOR_BGR2GRAY);

        // 2. Equalização de histograma
        //    Aumenta o contraste da imagem, tornando características faciais
        //    mais visíveis mesmo em condições de iluminação variável
        Mat equalized = new Mat();
        equalizeHist(gray, equalized);

        // 3. Detecção de rostos com detectMultiScale
        //    - scaleFactor: 1.1 = busca em 10% de redução por nível
        //    - minNeighbors: 5 = quantos vizinhos um candidato precisa (evita falsos positivos)
        //    - minSize: tamanho mínimo do rosto em pixels
        RectVector faces = new RectVector();
        classifier.detectMultiScale(
                equalized,
                faces,
                1.1,           // scaleFactor
                5,             // minNeighbors
                0,             // flags (0 = padrão)
                new Size(80, 80),   // minSize
                new Size(500, 500)  // maxSize
        );

        // Libera memória (importante em loops de captura!)
        gray.release();
        equalized.release();

        log.debug("Rostos detectados: {}", faces.size());
        return faces;
    }

    /**
     * Recorta apenas a região do rosto do frame.
     * Útil para salvar imagens de treino ou para o reconhecedor.
     *
     * @param frame Frame completo
     * @param faceRect Retângulo do rosto detectado
     * @return Mat contendo apenas o rosto em escala de cinza
     */
    public Mat extractFaceROI(Mat frame, Rect faceRect) {
        // ROI = Region of Interest
        Mat roi = new Mat(frame, faceRect);
        Mat gray = new Mat();
        cvtColor(roi, gray, COLOR_BGR2GRAY);

        // Redimensiona para tamanho padrão (importante para o reconhecedor LBPH)
        Mat resized = new Mat();
        resize(gray, resized, new Size(100, 100));

        return resized;
    }

    /**
     * Extrai o arquivo Haar Cascade XML dos recursos do classpath para
     * um arquivo temporário no disco (necessário para o OpenCV nativo).
     */
    private Path extractCascadeToTemp() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(CASCADE_RESOURCE);

        if (is == null) {
            // Tenta buscar direto da biblioteca JavaCV pelo caminho esperado
            String javacvPath = System.getProperty("user.home") + "/.javacpp/cache";
            File cascade = findCascadeInCache(new File(javacvPath));
            if (cascade != null) {
                return cascade.toPath();
            }
            throw new IOException("Arquivo " + CASCADE_RESOURCE + " não encontrado no classpath.");
        }

        Path temp = Files.createTempFile("haarcascade_", ".xml");
        temp.toFile().deleteOnExit();
        Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
        return temp;
    }

    /**
     * Busca o arquivo cascade no cache do JavaCV (fallback).
     */
    private File findCascadeInCache(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return null;
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                File found = findCascadeInCache(f);
                if (found != null) return found;
            } else if (f.getName().equals(CASCADE_RESOURCE)) {
                return f;
            }
        }
        return null;
    }
}