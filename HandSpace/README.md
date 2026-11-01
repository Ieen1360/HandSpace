# HandSpace

Aplicativo Android (Kotlin nativo) que transforma o celular em uma interface espacial controlada pelas mãos,
usando a câmera frontal e o MediaPipe Hand Landmarker. Todo o processamento é local: nenhuma imagem sai do aparelho.

## Estado atual: v0.3 — integração de gestos e interface espacial

Implementado e ligado ao fluxo real (câmera → landmarks → gestos → controlador → interface):

- Rastreamento de até duas mãos com os 21 landmarks, GPU com fallback para CPU, FPS e latência (já existente).
- Cursor espacial seguindo a ponta do indicador, suavizado e mapeado com a mesma transformação do overlay
  (espelhamento de selfie incluído). Cursor com anel quando apenas se move, e preenchido quando segura algo.
- Gestos:
  - **Apontar (indicador estendido):** move o cursor.
  - **Pinça:** seleciona. Botões disparam uma vez; painéis e objetos passam a ser arrastados.
  - **Mão aberta:** solta o elemento segurado.
  - **Punho fechado:** reconhecido e exibido, sem ação.
  - **Nenhum:** sem ação.
- Estabilidade: histerese na pinça, 3 frames consecutivos para validar um gesto e cooldown de 400 ms
  entre seleções. Pinça contínua não gera várias seleções.
- Duas mãos independentes, indexadas pela lateralidade. Mão que sai do campo de visão solta o que segurava.
- Interface espacial de demonstração, com todos os elementos respondendo aos gestos:
  - Painel de informações (arrastável) com gesto atual, número de mãos, arrastes concluídos e cor do objeto.
  - Objeto circular arrastável com feedback de elevação ao ser segurado.
  - Botão **Reorganizar**: volta os elementos às posições iniciais.
  - Botão **Mudar cor**: troca a cor do objeto.
  - Feedback visual de apontar, selecionar, arrastar e de acionamento de botão.
- Diagnóstico de sensores: giroscópio e acelerômetro, lidos com `SENSOR_DELAY_UI`. Se um sensor não existir,
  o texto informa "indisponível" e nenhum valor é inventado. Os sensores não controlam a interface.
- Indicador de gesto atual e painel de status com FPS e latência, atualizado a cada 250 ms.
- Indicador visível de uso da câmera.

## Requisitos

- Android Studio Ladybug (2024.2) ou mais recente.
- JDK 17.
- Android 8.0 (API 26) ou superior, com câmera frontal.

## Como executar

1. Baixe o modelo (uma vez):
   ```bash
   ./scripts/download_model.sh
   ```
   Ele grava `app/src/main/assets/hand_landmarker.task`. Sem esse arquivo, o app mostra erro na tela.
2. Abra a pasta `HandSpace` no Android Studio, ou rode o workflow do GitHub Actions, que gera o APK de debug.
3. Execute em um aparelho Android real. O emulador normalmente não tem câmera frontal útil nem GPU para o delegate.

## Estrutura

```
app/src/main/java/com/handspace/
├── MainActivity.kt                    orquestra câmera, tracking, interação, UI e sensores
├── camera/FrontCameraController.kt    CameraX: preview, análise e orientação dos frames
├── tracking/                          MediaPipe: HandTracker, modelos, presença, ViewTransform, overlay
├── gesture/                           HandPoseAnalyzer e GestureRecognizer (PINCH, OPEN_HAND, FIST, POINTING)
├── interaction/
│   ├── HandInteractionController.kt   gestos → ações: cursor, seleção, arrasto, soltura, cooldown
│   ├── SpatialScene.kt                modelo da cena: elementos, ordem de empilhamento, botões, layout
│   ├── CursorSmoother.kt              suavização do cursor
│   └── ActivationCooldown.kt          intervalo mínimo entre ativações
├── ui/SpatialSceneView.kt             desenho da cena e dos cursores (sem toques)
├── sensors/DeviceSensorManager.kt     leitura de giroscópio e acelerômetro para diagnóstico
└── util/FpsCounter.kt
```

## Testes

Testes unitários na JVM, sem dispositivo:

- `gesture/GestureRecognizerTest`: classificação, prioridade, histerese da pinça e debounce.
- `interaction/HandInteractionControllerTest`: seleção, arrasto, soltura, cooldown, duas mãos, perda de mão,
  suavização e mapeamento pela transformação da view.
- `interaction/SpatialSceneTest`: hit-test, ordem de empilhamento, clamp, reescala, reset, ciclo de cores e layout.
- `tracking/*`: mapeamento de coordenadas, presença e índices dos landmarks.

As mãos dos testes são sintéticas e servem para exercitar a lógica. O comportamento real depende da câmera
e do MediaPipe, e precisa ser validado no aparelho.

## Limitações conhecidas

- O alinhamento overlay↔preview e o espelhamento assumem `FILL_CENTER` e selfie. Valide no aparelho que o cursor
  cobre a ponta do indicador; se houver deslocamento, o ajuste fica em `ViewTransform.kt`.
- A lateralidade (`LEFT`/`RIGHT`) depende da imagem espelhada. Se estiver invertida no aparelho, o controle por
  mão (por slot) continua funcionando, mas o rótulo exibido na view fica trocado.
- Mão aberta solta o elemento; fechar a pinça sem abrir a mão mantém o elemento seguro. É a regra da especificação.
- O cálculo de interação roda na thread principal, junto com a renderização. O custo é proporcional ao número de
  mãos e elementos (microssegundos), mas não foi medido em aparelhos intermediários.
- Cada frame ainda cria um Bitmap novo para orientar a imagem. Buffers reutilizáveis ficam para a fase de desempenho.
- Não há persistência de layout entre execuções.
