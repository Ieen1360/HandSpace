# HandSpace

Aplicativo Android (Kotlin nativo) que transforma o celular em uma interface controlada pelas mãos,
usando a câmera frontal e o MediaPipe Hand Landmarker. Todo o processamento é local.

## Estado atual: Fase 1 — rastreamento real

Implementado:

- Permissão de câmera com tratamento de negação.
- Câmera frontal via CameraX (preview + análise em 640x480, descarte de frames atrasados).
- Detecção de até 2 mãos com os 21 landmarks do MediaPipe, em modo LIVE_STREAM.
- Desenho do esqueleto e dos landmarks sobre o preview, com cores por lateralidade.
- Detecção de entrada e saída de mãos do campo de visão (transições).
- Indicadores de FPS de rastreamento e latência captura→resultado.
- Indicador visível de uso da câmera.
- Modelo de dados próprio (`HandFrame`, `TrackedHand`, `Landmark`) isolado do SDK.
- Testes unitários para o mapeamento de coordenadas, presença e índices.

Ainda não implementado (próximas fases, nesta ordem): reconhecimento de gestos (pinça, mão aberta,
punho fechado), sensores e calibração, interface 3D, menu, configurações persistentes.

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
2. Abra a pasta `HandSpace` no Android Studio. Como o projeto não inclui o `gradle-wrapper.jar`,
   gere-o uma vez com um Gradle 8.9 instalado: `gradle wrapper --gradle-version 8.9`.
3. Sincronize o Gradle e execute em um aparelho Android real (o emulador costuma não ter câmera frontal útil nem GPU para o delegate).

## Estrutura

```
app/src/main/java/com/handspace/
├── MainActivity.kt                 orquestra câmera, rastreador e UI
├── camera/FrontCameraController.kt CameraX: preview, análise e orientação dos frames
├── tracking/
│   ├── HandTracker.kt              wrapper do MediaPipe (GPU→CPU, descarte de frames)
│   ├── HandModels.kt               modelos próprios e índices dos landmarks
│   ├── HandPresenceTracker.kt      transições de entrada/saída de mão
│   ├── ViewTransform.kt            imagem → pixels da view (FILL_CENTER)
│   └── LandmarkOverlayView.kt      desenho dos landmarks
└── util/FpsCounter.kt
```

Os próximos módulos previstos são `gestures/`, `sensors/`, `scene/` (interface 3D) e `settings/`.
Eles consomem `HandFrame`, então o rastreamento não precisa ser alterado para recebê-los.

## Limitações conhecidas

- **Não compilado nem testado em aparelho nesta etapa.** O código foi escrito sem acesso ao Gradle
  nem a um dispositivo. Espere ajustes em versões de dependência e no mapeamento de coordenadas.
- O mapeamento overlay↔preview assume `FILL_CENTER` e espelhamento de selfie. Valide visualmente
  que os landmarks cobrem a mão; se houver deslocamento, o ajuste fica em `ViewTransform.kt`.
- A lateralidade (`LEFT`/`RIGHT`) depende da imagem espelhada. Confirme no aparelho e, se
  estiver invertida, inverta na conversão em `HandTracker.kt`. Isso também é necessário para a configuração de "mão dominante".
- A cada frame é criado um Bitmap novo para orientar a imagem. É simples e correto, mas custa CPU e
  GC. Otimizar com buffers reutilizáveis faz parte da fase de desempenho.
- A taxa de frames e a latência ainda não foram medidas em aparelhos intermediários.
