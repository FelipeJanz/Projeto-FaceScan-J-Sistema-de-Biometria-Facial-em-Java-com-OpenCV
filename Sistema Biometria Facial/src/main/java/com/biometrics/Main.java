package com.biometrics;

import com.biometrics.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ponto de entrada da aplicação de Biometria Facial.
 *
 * Fluxo principal:
 *   1. Inicializa a janela principal (Swing)
 *   2. A janela instancia os serviços necessários
 *   3. O usuário interage via botões da UI
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("========================================");
        log.info("  Sistema de Biometria Facial v1.0.0   ");
        log.info("========================================");

        // Inicia a interface gráfica na Event Dispatch Thread (EDT) do Swing
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                // Aparência nativa do sistema operacional
                javax.swing.UIManager.setLookAndFeel(
                        javax.swing.UIManager.getSystemLookAndFeelClassName()
                );
            } catch (Exception e) {
                log.warn("Não foi possível definir o Look and Feel nativo: {}", e.getMessage());
            }

            MainWindow window = new MainWindow();
            window.setVisible(true);
            log.info("Interface gráfica iniciada com sucesso.");
        });
    }
}