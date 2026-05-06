package com.biometrics.service;

import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

/**
 * DatasetManager
 *
 * Responsabilidade: gerenciar o sistema de arquivos do dataset.
 *
 * Estrutura criada no disco:
 *
 *   dataset/
 *   ├── labels.txt          ← mapeia ID numérico para nome
 *   ├── joao/
 *   │   ├── face_001.png
 *   │   ├── face_002.png
 *   │   └── ...
 *   └── maria/
 *       ├── face_001.png
 *       └── ...
 *
 * Por que salvar as imagens?
 *   O LBPH precisa ser retreinado toda vez que novos usuários são adicionados.
 *   Ao salvar as imagens no disco, podemos retreinar sem precisar recapturar.
 */
public class DatasetManager {

    private static final Logger log = LoggerFactory.getLogger(DatasetManager.class);

    /** Diretório raiz do dataset */
    private static final String DATASET_DIR = "dataset";

    /** Arquivo que persiste o mapeamento ID → Nome */
    private static final String LABELS_FILE = DATASET_DIR + "/labels.txt";

    /** Quantas imagens capturar por usuário (recomendado: 30-50) */
    public static final int IMAGES_PER_USER = 60;

    /**
     * Salva uma imagem de rosto no dataset do usuário.
     *
     * @param userName Nome do usuário (será o nome da pasta)
     * @param faceImage Mat do rosto em escala de cinza (100x100)
     * @param index Índice sequencial da imagem
     */
    public void saveFaceImage(String userName, Mat faceImage, int index) {
        String userDir = DATASET_DIR + "/" + sanitizeName(userName);
        createDirIfAbsent(userDir);

        String filename = String.format("%s/face_%03d.png", userDir, index);
        boolean saved = imwrite(filename, faceImage);

        if (saved) {
            log.debug("Imagem salva: {}", filename);
        } else {
            log.error("Falha ao salvar imagem: {}", filename);
        }
    }

    /**
     * Carrega todas as imagens do dataset e retorna junto com o labelMap.
     *
     * @return DatasetBundle com imagens + mapa de labels
     */
    public DatasetBundle loadDataset() throws IOException {
        File datasetDir = new File(DATASET_DIR);
        if (!datasetDir.exists() || !datasetDir.isDirectory()) {
            throw new IOException("Diretório de dataset não encontrado: " + DATASET_DIR);
        }

        Map<Integer, String> labelMap = loadLabelMap();
        List<Mat> images = new ArrayList<>();
        List<Integer> labelsList = new ArrayList<>();

        // Ordena os usuários por ID para consistência
        List<Map.Entry<Integer, String>> entries = new ArrayList<>(labelMap.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, String> entry : entries) {
            int id = entry.getKey();
            String name = entry.getValue();
            String userDir = DATASET_DIR + "/" + sanitizeName(name);

            File dir = new File(userDir);
            if (!dir.exists()) {
                log.warn("Diretório do usuário '{}' não encontrado: {}", name, userDir);
                continue;
            }

            File[] pngFiles = dir.listFiles(f -> f.getName().endsWith(".png"));
            if (pngFiles == null || pngFiles.length == 0) {
                log.warn("Nenhuma imagem encontrada para o usuário: {}", name);
                continue;
            }

            for (File img : pngFiles) {
                // Carrega como escala de cinza (flag 0)
                Mat mat = imread(img.getAbsolutePath(), IMREAD_GRAYSCALE);
                if (!mat.empty()) {
                    images.add(mat);
                    labelsList.add(id);
                }
            }

            log.info("Usuário '{}' (ID {}): {} imagens carregadas", name, id, pngFiles.length);
        }

        log.info("Dataset carregado: {} imagens de {} usuários", images.size(), labelMap.size());
        return new DatasetBundle(images, labelsList, labelMap);
    }

    /**
     * Registra um novo usuário no labels.txt, gerando um ID único.
     *
     * @param userName Nome do usuário
     * @return ID gerado para este usuário
     */
    public int registerUser(String userName) throws IOException {
        Map<Integer, String> labelMap = loadLabelMap();

        // Verifica se já existe
        for (Map.Entry<Integer, String> e : labelMap.entrySet()) {
            if (e.getValue().equalsIgnoreCase(userName)) {
                log.info("Usuário '{}' já registrado com ID {}", userName, e.getKey());
                return e.getKey();
            }
        }

        // Novo ID = max + 1 (ou 0 se vazio)
        int newId = labelMap.keySet().stream().mapToInt(i -> i).max().orElse(-1) + 1;
        labelMap.put(newId, userName);
        saveLabelMap(labelMap);

        log.info("Novo usuário registrado: '{}' com ID {}", userName, newId);
        return newId;
    }

    /**
     * Carrega o mapa ID → Nome do arquivo labels.txt.
     */
    public Map<Integer, String> loadLabelMap() throws IOException {
        Map<Integer, String> map = new LinkedHashMap<>();
        File file = new File(LABELS_FILE);

        if (!file.exists()) {
            return map; // Retorna vazio se não existe ainda
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.contains(":")) continue;
                String[] parts = line.split(":", 2);
                try {
                    int id = Integer.parseInt(parts[0].trim());
                    String name = parts[1].trim();
                    map.put(id, name);
                } catch (NumberFormatException e) {
                    log.warn("Linha inválida no labels.txt: {}", line);
                }
            }
        }

        return map;
    }

    /**
     * Retorna quantas imagens existem no dataset de um usuário.
     */
    public int getImageCount(String userName) {
        File dir = new File(DATASET_DIR + "/" + sanitizeName(userName));
        if (!dir.exists()) return 0;
        File[] files = dir.listFiles(f -> f.getName().endsWith(".png"));
        return files == null ? 0 : files.length;
    }

    // ─── Métodos auxiliares ──────────────────────────────────────

    private void saveLabelMap(Map<Integer, String> map) throws IOException {
        createDirIfAbsent(DATASET_DIR);
        try (PrintWriter writer = new PrintWriter(new FileWriter(LABELS_FILE))) {
            for (Map.Entry<Integer, String> e : map.entrySet()) {
                writer.println(e.getKey() + ":" + e.getValue());
            }
        }
    }

    private void createDirIfAbsent(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /** Remove caracteres inválidos para nomes de pasta */
    private String sanitizeName(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9_\\-]", "_")
                .replaceAll("_{2,}", "_");
    }

    // ─── Inner class: Bundle de retorno ──────────────────────────

    /**
     * Agrupa os dados do dataset para facilitar a passagem entre métodos.
     */
    public static class DatasetBundle {
        public final List<Mat> images;
        public final List<Integer> labels;
        public final Map<Integer, String> labelMap;

        public DatasetBundle(List<Mat> images, List<Integer> labels, Map<Integer, String> labelMap) {
            this.images = images;
            this.labels = labels;
            this.labelMap = labelMap;
        }

        /** Reconstrói o array de labels para uso no treino */
        public int[] getLabelsArray() {
            return labels.stream().mapToInt(i -> i).toArray();
        }
    }
}