Sistema de biometria facial em tempo real desenvolvido em Java. Captura imagens da webcam, detecta rostos com Haar Cascade e os reconhece usando o algoritmo LBPH. O foco é mostrar como construir um pipeline completo de visão computacional em Java, uma linguagem dominante no mercado corporativo mas raramente associada a esse tipo de projeto.

Tecnologias e motivações:

Java 17 — LTS mais moderna e amplamente adotada, com boa performance e compatibilidade empresarial.
OpenCV — biblioteca de visão computacional mais usada no mundo, com algoritmos otimizados em C++ acessíveis pelo Java.
JavaCV — elimina a necessidade de configurar manualmente bibliotecas nativas do OpenCV em cada sistema operacional. Os binários são baixados automaticamente via Maven, tornando o projeto portável sem configuração extra.
Maven — gerencia dependências pesadas com binários nativos de forma estável. O plugin maven-shade gera um JAR executável único com tudo incluído.
Swing — já faz parte do JDK, sem dependências extras. Suficiente para uma UI funcional em um projeto com foco em visão computacional.

Como os algoritmos funcionam:

Haar Cascade varre a imagem em múltiplas escalas buscando padrões faciais aprendidos em treinamento. É rápido o suficiente para rodar em tempo real na CPU, já vem embutido no OpenCV e funciona bem em ambientes controlados com câmera frontal.
LBPH (Local Binary Patterns Histograms) analisa a textura do rosto comparando cada pixel com seus 8 vizinhos, gerando uma assinatura única. É robusto a variações de iluminação, funciona com poucos dados de treino (20 a 30 imagens por pessoa) e o modelo pode ser salvo em disco e recarregado sem retreinar.
Pré-processamento aplica conversão para escala de cinza, equalização de histograma e filtro Gaussiano antes de treinar ou reconhecer, o que melhora significativamente a precisão em condições reais de iluminação.

Arquitetura
Cada classe tem uma responsabilidade única e bem definida.

- CameraService — abre, captura frames e fecha a webcam
- FaceDetectorService — detecta rostos e recorta a região de interesse
- FaceRecognizerService — treina, reconhece e persiste o modelo LBPH
- DatasetManager — organiza imagens em disco e mantém o mapeamento de usuários
- ImageUtils — converte Mat para BufferedImage e desenha os elementos visuais
- MainWindow — conecta todos os serviços e gerencia o estado da aplicação

Como usar

Clique em Cadastrar Usuário, informe um nome e posicione o rosto na câmera — 30 imagens são capturadas automaticamente
Repita para cada pessoa desejada
Clique em Treinar Modelo — o modelo é salvo e recarregado nas próximas execuções
Clique em Reconhecer Rosto para identificar em tempo real

Confiança abaixo de 30 indica reconhecimento confiável. Acima de 80 o rosto é considerado desconhecido.

Próximos passos possíveis
Substituir o LBPH por uma rede neural via módulo DNN do OpenCV, carregando modelos ONNX como FaceNet ou ArcFace para maior precisão. Outra evolução natural é expor os serviços como API REST com Spring Boot, permitindo integração com aplicações web ou mobile.
