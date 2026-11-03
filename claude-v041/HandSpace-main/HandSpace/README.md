# HandSpace

Aplicativo Android (Kotlin nativo) que transforma o celular em uma interface controlada pelas mãos, com
rastreamento do MediaPipe Hand Landmarker, câmera frontal e traseira, e um mundo virtual em perspectiva.
Todo o processamento é local: nenhuma imagem sai do aparelho.

## Estado atual: v0.4

**Status de verificação:** esta versão não foi compilada nem testada em aparelho pelo autor. A build
depende do GitHub Actions (`testDebugUnitTest` e `assembleDebug`). Veja a seção "Verificação".

### O que a v0.4 contém

- **Câmera frontal e traseira com troca.** Uma câmera por vez, sempre liberada antes da outra abrir
  (`CameraModeManager`). O botão "Trocar câmera" e o item "Câmera" do menu espacial fazem a troca.
  Estados em tela: qual câmera está ativa e se as âncoras espaciais estão disponíveis.
- **Rastreamento das mãos nas duas câmeras.** A análise do MediaPipe roda na câmera ativa. A imagem é
  espelhada só na frontal, para coincidir com o preview de selfie.
- **Mãos 3D em perspectiva.** Os 21 landmarks são projetados em um espaço 3D (`HandSkeleton3D`), com
  osso e junta com espessura pela profundidade. Mão aberta, punho, pinça e apontar aparecem pela própria
  geometria dos landmarks.
- **Mundo virtual em perspectiva** (`SpatialWorld`, `Camera3D`), desenhado sobre o preview. Contém:
  - Menu espacial com cinco botões, cada um com ação real:
    - **Início:** volta objetos à posição inicial.
    - **Objetos:** mostra ou oculta a esfera.
    - **Config.:** mostra ou oculta o esqueleto das mãos.
    - **Rastreio:** mostra ou oculta o painel de informações.
    - **Câmera:** troca entre frontal e traseira.
  - Painel de informações arrastável, com gesto, mãos, arrastes, câmera e estado das âncoras.
  - Esfera arrastável, que pode ser agarrada, puxada e escalada com duas mãos.
- **Interação 3D por gestos** (`SpatialWorldController`):
  - **Apontar:** o raio sai da câmera virtual pela ponta do indicador. Ele mostra o alvo sob o dedo.
  - **Pinça:** aciona botão uma vez, ou agarra painel/esfera.
  - **Afastar ou aproximar a mão no eixo de profundidade:** puxa ou empurra o objeto agarrado.
  - **Duas mãos no mesmo objeto:** a escala acompanha a distância entre as pontas dos indicadores.
  - **Mão aberta:** solta. **Punho:** reconhecido, sem ação. Mão que sai da visão solta tudo.
- **Bola interativa da v0.3 preservada.** No modo frontal, a interface 2D da v0.3 (bola, painéis,
  botões Reorganizar e Mudar cor) continua funcionando sem alteração de comportamento.
- **Sensores, FPS, latência, indicador de gesto e indicador de câmera ativa** da v0.3, sem alteração.

### O que NÃO está na v0.4

- **ARCore não está integrado.** A decisão de usar ARCore com fallback não foi implementada nesta versão.
  O motivo é concreto: a versão da dependência, a API e a compatibilidade do aparelho precisam ser
  verificadas contra o Maven e a lista oficial do Google Play Services for AR, e isso não pôde ser feito.
  Sem essa verificação, uma dependência errada quebraria a build inteira.
- **Âncoras espaciais não existem.** Os objetos seguem a projeção da câmera, não o ambiente real.
  Quando o aparelho é movido, eles se movem junto com a imagem. A interface mostra "Âncoras: indisponíveis
  (sem ARCore)" o tempo todo. Isso é o fallback, não uma simulação de ARCore.
- **Renderização sem OpenGL.** As mãos e os objetos são desenhados com projeção em perspectiva no `Canvas`.
  É mais simples e mais fácil de verificar, mas não tem iluminação nem malha 3D de verdade.
- **Profundidade das mãos é aproximada.** O z dos landmarks do MediaPipe não é métrico. A profundidade
  é estimada em torno de 0,6 m e varia com o z relativo dos landmarks. O alinhamento lateral e vertical
  vem da projeção do landmark na tela, então a mão fica sobre a imagem real.
- **Sem captura simultânea** das duas câmeras, como especificado.
- **Hand tracking na traseira depende do aparelho.** Sem rastreamento, o modo AR ainda mostra os objetos,
  mas nenhuma mão é detectada.
- **Controle do Android pelas mãos** (AccessibilityService) não foi implementado.

### Requisitos

- Android Studio Ladybug (2024.2) ou mais recente.
- JDK 17.
- Android 8.0 (API 26) ou superior, com câmera frontal. A traseira é necessária para o modo AR.

### Como executar

1. Baixe o modelo (uma vez):
   ```bash
   ./scripts/download_model.sh
   ```
   Ele grava `app/src/main/assets/hand_landmarker.task`. Sem esse arquivo, o app mostra erro na tela.
2. Abra a pasta `HandSpace` no Android Studio, ou rode o workflow do GitHub Actions, que gera o APK de debug.
3. Execute em um aparelho Android real. O emulador normalmente não tem câmera útil nem GPU para o delegate.

### Estrutura

```
app/src/main/java/com/handspace/
├── MainActivity.kt                       orquestra câmera, interação 2D e 3D, UI e sensores
├── camera/
│   ├── CameraModeManager.kt              máquina de estados: uma câmera por vez, liberar antes de abrir
│   └── FrontCameraController.kt          CameraX para frontal e traseira; análise e orientação dos frames
├── tracking/                             MediaPipe: HandTracker, modelos, presença, ViewTransform, overlay
├── gesture/                              HandPoseAnalyzer e GestureRecognizer (PINCH, OPEN_HAND, FIST, POINTING)
├── interaction/
│   ├── HandInteractionController.kt      interação 2D da v0.3 (bola e painéis na tela)
│   ├── SpatialWorldController.kt         interação 3D: raios, agarrar, puxar, escalar, botões do menu
│   ├── SpatialScene.kt                   modelo 2D da v0.3
│   ├── CursorSmoother.kt                 suavização do cursor
│   └── ActivationCooldown.kt             intervalo mínimo entre ativações
├── spatial/
│   ├── SpatialGeometry.kt                vetores, raios, plano, esfera, painel, escala
│   ├── Camera3D.kt                       projeção em perspectiva e raio a partir de um pixel
│   ├── SpatialWorld.kt                   objetos do mundo, menu e layout da demonstração
│   └── HandSkeleton3D.kt                 21 landmarks → pontos 3D
├── ui/
│   ├── SpatialSceneView.kt               desenho 2D da v0.3
│   └── SpatialWorldView.kt               desenho do mundo 3D, das mãos e dos cursores
├── sensors/DeviceSensorManager.kt        giroscópio e acelerômetro para diagnóstico
└── util/FpsCounter.kt
```

### Testes

Testes unitários na JVM, sem dispositivo. Cobrem a lógica, não o hardware.

- `camera/CameraModeManagerTest`: troca de câmera, nunca duas abertas, pedidos pendentes, falhas e parada.
- `spatial/SpatialGeometryTest`: planos, painéis, esferas, escolha do mais próximo e escala.
- `spatial/Camera3DTest`: projeção, ida e volta entre pixel e raio, distância focal.
- `spatial/SpatialWorldTest`: escolha de objetos, visibilidade, reset e layout do menu dentro da tela.
- `spatial/HandSkeleton3DTest`: 21 pontos, punho na profundidade nominal, ponta mais próxima tem profundidade menor.
- `interaction/SpatialWorldControllerTest`: agarrar e soltar, modo frontal não agarra, botões uma vez,
  Início, Objetos, Config., duas mãos com escala, reset.
- `interaction/HandInteractionControllerTest`, `SpatialSceneTest`, `GestureRecognizerTest` e os de
  tracking: da v0.3, preservados. A correção da histerese da pinça continua no lugar.

As mãos dos testes são sintéticas. Elas testam a lógica, não o rastreamento real.

### Verificação

- **Compilação:** não executada pelo autor. Não há Gradle nem Android SDK no ambiente de desenvolvimento.
- **Testes unitários:** não executados pelo autor.
- **Em aparelho:** não testado.
- Verificações feitas: balanceamento de chaves e parênteses em todos os arquivos Kotlin, referências
  `R.*` contra `strings.xml`, ids do layout contra os `binding.*` usados, nomes duplicados em `strings.xml`.

### Limitações conhecidas

- O alinhamento do cursor e do desenho com o preview precisa ser validado no aparelho. Se houver
  deslocamento, o ajuste fica em `ViewTransform.kt`.
- A lateralidade (`LEFT`/`RIGHT`) pode vir trocada no modo frontal por causa do espelhamento. Por isso,
  o controle usa uma `HandSlot` por lateralidade, e o rótulo exibido pode estar trocado.
- Mão aberta solta o objeto; fechar a pinça sem abrir a mão mantém o objeto seguro. É a regra da especificação.
- Botões não podem ser acionados enquanto a mão segura outro objeto.
- A interação roda na thread principal, junto com a renderização. Não foi medida em aparelhos intermediários.
- Cada frame ainda cria um Bitmap para orientar a imagem. Buffers reutilizáveis ficam para a fase de desempenho.
- Não há persistência de layout entre execuções.

### Próximos passos

1. Integrar ARCore com verificação de compatibilidade em tempo de execução, com o fallback atual preservado.
2. Renderização em OpenGL ES, se a qualidade visual do Canvas não for suficiente.
3. Controle do Android pelas mãos, investigado separadamente, com AccessibilityService explícito e ativado pelo usuário.
