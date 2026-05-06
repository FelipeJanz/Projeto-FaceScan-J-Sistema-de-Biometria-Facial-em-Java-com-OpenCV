package com.biometrics.model;

/**
 * RecognitionResult
 *
 * Encapsula o resultado de um reconhecimento facial:
 * - O nome da pessoa identificada (ou "Desconhecido")
 * - A confiança do reconhecimento (0 = perfeito, 100+ = muito incerto)
 * - Se foi reconhecido ou não
 *
 * Padrão de uso: factory methods estáticos (recognized/unknown)
 */
public class RecognitionResult {

    private final String name;
    private final double confidence;
    private final boolean recognized;
    private final String message;

    // ─── Factory methods ──────────────────────────────────────────

    /**
     * Cria resultado positivo (pessoa reconhecida).
     */
    public static RecognitionResult recognized(String name, double confidence) {
        return new RecognitionResult(name, confidence, true, null);
    }

    /**
     * Cria resultado negativo (desconhecido) com confiança.
     */
    public static RecognitionResult unknown(double confidence) {
        return new RecognitionResult("Desconhecido", confidence, false, null);
    }

    /**
     * Cria resultado negativo com mensagem de erro/status.
     */
    public static RecognitionResult unknown(String message) {
        return new RecognitionResult("Desconhecido", -1, false, message);
    }

    // ─── Construtor ───────────────────────────────────────────────

    private RecognitionResult(String name, double confidence, boolean recognized, String message) {
        this.name = name;
        this.confidence = confidence;
        this.recognized = recognized;
        this.message = message;
    }

    // ─── Getters ──────────────────────────────────────────────────

    public String getName() { return name; }

    public double getConfidence() { return confidence; }

    public boolean isRecognized() { return recognized; }

    public String getMessage() { return message; }

    /**
     * Formata o resultado para exibição na interface.
     * Exemplos:
     *   "João Silva (Conf: 45.2)"
     *   "Desconhecido (Conf: 95.3)"
     */
    public String toDisplayString() {
        if (message != null) {
            return name + " [" + message + "]";
        }
        return String.format("%s (Conf: %.1f)", name, confidence);
    }

    /**
     * Classificação textual da confiança para fins de UI.
     */
    public String getConfidenceLabel() {
        if (confidence < 0) return "N/A";
        if (confidence < 30) return "Alta";
        if (confidence < 60) return "Média";
        if (confidence < 80) return "Baixa";
        return "Muito Baixa";
    }

    @Override
    public String toString() {
        return "RecognitionResult{name='" + name + "', confidence=" + confidence
                + ", recognized=" + recognized + "}";
    }
}