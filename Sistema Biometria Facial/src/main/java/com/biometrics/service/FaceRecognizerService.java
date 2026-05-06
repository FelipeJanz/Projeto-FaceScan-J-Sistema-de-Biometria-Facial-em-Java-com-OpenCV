package com.biometrics.service;

import com.biometrics.model.RecognitionResult;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bytedeco.opencv.global.opencv_core.CV_32SC1;

/**
 * FaceRecognizerService
 *
 * Responsabilidade: treinar e usar o LBPHFaceRecognizer para
 * identificar rostos cadastrados.
 *
 * O que é LBPH (Local Binary Patterns Histograms)?
 * ─────────────────────────────────────────────────
 * O LBPH é um algoritmo que descreve a textura de uma imagem.
 * Para cada pixel, ele compara com seus 8 vizinhos:
 *   - Se o vizinho >= pixel central → bit 1
 *   - Se o vizinho <  pixel central → bit 0
 * Isso gera um número binário de 8 bits (LBP code) para cada pixel.
 * O histograma desses códigos é usado como "assinatura" do rosto.
 *
 * Vantagens do LBPH:
 *   ✅ Robusto a variações de iluminação
 *   ✅ Simples e rápido
 *   ✅ Funciona bem com pouco dados (10-30 imagens por pessoa)
 *   ✅ Pode ser atualizado incrementalmente
 *
 * Como interpretar a Confiança:
 *   - < 50  → Reconhecimento muito confiável
 *   - 50-80 → Reconhecimento aceitável
 *   - > 80  → Pouco confiável (provavelmente desconhecido)
 *   - > 100 → Considerado desconhecido
 */
public class FaceRecognizerService {

    private static final Logger log = LoggerFactory.getLogger(FaceRecognizerService.class);

    /**
     * Limiar de confiança: acima disso, considera "Desconhecido".
     * O LBPH usa distância, então MENOR = mais parecido.
     */
    public static final double CONFIDENCE_THRESHOLD = 80.0;

    /** Objeto nativo do OpenCV para reconhecimento facial com LBPH */
    private LBPHFaceRecognizer recognizer;

    /** Indica se o modelo foi treinado/carregado */
    private boolean trained = false;

    /**
     * Inicializa o reconhecedor LBPH com parâmetros padrão.
     *
     * Parâmetros:
     *  param radius    raio dos LBP vizinhos (1 = 8 vizinhos)
     *  param neighbors número de pontos no círculo
     *  param gridX     divisão horizontal para o histograma
     *  param gridY     divisão vertical para o histograma
     */
    public void initialize() {
        log.info("Inicializando LBPHFaceRecognizer...");
        recognizer = LBPHFaceRecognizer.create();
        log.info("LBPHFaceRecognizer criado com sucesso.");
    }

    /**
     * Treina o modelo com as imagens e rótulos fornecidos.
     *
     * @param images  Lista de Mats em escala de cinza (100x100)
     * @param labelMap Mapeamento de ID (int) → Nome do usuário
     */
    public void train(List<Mat> images, Map<Integer, String> labelMap) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("Lista de imagens vazia. Não é possível treinar.");
        }

        log.info("Iniciando treinamento com {} imagens de {} usuários...",
                images.size(), labelMap.size());

        // Monta o array de labels (inteiros) correspondente a cada imagem
        int[] labelsArray = buildLabelsArray(images, labelMap);

        // Converte List<Mat> para MatVector (formato exigido pelo OpenCV)
        MatVector imageVector = new MatVector(images.toArray(new Mat[0]));

        // Mat de labels no formato CV_32SC1 (inteiro de 32 bits, 1 canal)
        Mat labelsMat = new Mat(images.size(), 1, CV_32SC1);
        for (int i = 0; i < labelsArray.length; i++) {
            labelsMat.ptr(i).putInt(labelsArray[i]);
        }

        // Executa o treinamento
        recognizer.train(imageVector, labelsMat);
        trained = true;

        log.info("Treinamento concluído com sucesso!");
        log.info("Usuários cadastrados: {}", labelMap);
    }

    /**
     * Reconhece um rosto (Mat cinza 100x100).
     *
     * @param faceROI Imagem do rosto em escala de cinza
     * @param labelMap Mapa ID → Nome para traduzir o resultado
     * @return RecognitionResult com o nome e a confiança
     */
    public RecognitionResult recognize(Mat faceROI, Map<Integer, String> labelMap) {
        if (!trained) {
            return RecognitionResult.unknown("Modelo não treinado");
        }

        // Arrays para receber resultado do OpenCV
        int[] predictedLabel = {-1};
        double[] confidence = {0.0};

        recognizer.predict(faceROI, predictedLabel, confidence);

        double conf = confidence[0];
        int label = predictedLabel[0];

        log.debug("Predição → label={}, confiança={:.2f}", label, conf);

        // Avalia o limiar
        if (conf > CONFIDENCE_THRESHOLD || label < 0) {
            return RecognitionResult.unknown(conf);
        }

        String name = labelMap.getOrDefault(label, "ID:" + label);
        return RecognitionResult.recognized(name, conf);
    }

    /**
     * Salva o modelo treinado em arquivo YAML.
     * Este arquivo pode ser carregado depois sem precisar retreinar.
     *
     * @param filePath caminho do arquivo (ex: "models/modelo.yml")
     */
    public void saveModel(String filePath) {
        if (!trained) {
            throw new IllegalStateException("Nenhum modelo treinado para salvar.");
        }

        File dir = new File(filePath).getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        recognizer.save(filePath);
        log.info("Modelo salvo em: {}", filePath);
    }

    /**
     * Carrega um modelo previamente salvo.
     *
     * @param filePath caminho do arquivo YAML
     */
    public void loadModel(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Arquivo de modelo não encontrado: " + filePath);
        }

        if (recognizer == null) {
            initialize();
        }

        recognizer.read(filePath);
        trained = true;
        log.info("Modelo carregado de: {}", filePath);
    }

    /** @return true se o modelo está treinado/carregado e pronto para uso */
    public boolean isTrained() {
        return trained;
    }

    /**
     * Constrói o array de labels para o treinamento.
     * Cada imagem na lista recebe o ID inteiro do usuário correspondente.
     */
    private int[] buildLabelsArray(List<Mat> images, Map<Integer, String> labelMap) {
        // Determina quantas imagens cada ID tem (assumindo distribuição igual)
        int numUsers = labelMap.size();
        int imagesPerUser = images.size() / numUsers;
        int[] labels = new int[images.size()];

        List<Integer> ids = new ArrayList<>(labelMap.keySet());
        for (int i = 0; i < images.size(); i++) {
            labels[i] = ids.get(i / imagesPerUser);
        }
        return labels;
    }
}