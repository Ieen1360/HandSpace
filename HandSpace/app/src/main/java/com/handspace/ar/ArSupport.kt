package com.handspace.ar

/**
 * Resultado de uma verificação de suporte ao ARCore, independente do SDK.
 * O mapeamento a partir de `ArCoreApk.Availability` fica em [ArSessionManager].
 */
enum class ArSupport {
    /** Resposta transitória do SDK: a verificação ainda não terminou. Deve ser repetida. */
    UNKNOWN_CHECKING,

    /** O SDK reportou erro ao verificar. Não é transitório. */
    UNKNOWN_ERROR,

    /** O SDK não respondeu a tempo. Pode ser repetido pelo usuário. */
    UNKNOWN_TIMED_OUT,

    /** O dispositivo não está na lista de compatibilidade do ARCore. */
    UNSUPPORTED_DEVICE,

    /** Compatível, mas os serviços do ARCore não estão instalados. */
    NEEDS_INSTALL,

    /** Compatível, mas a versão instalada dos serviços é antiga. */
    NEEDS_UPDATE,

    /** Compatível e instalado. */
    SUPPORTED;

    /** Verdadeiro somente quando uma sessão pode ser criada agora. */
    val isReady: Boolean get() = this == SUPPORTED
}

/** Resultado de uma tentativa de instalação ou atualização dos serviços do ARCore. */
enum class ArInstallOutcome {
    /** Não havia instalação pendente: nada foi pedido. */
    NOT_REQUIRED,

    /** Instalação pedida ao usuário e ainda em andamento. */
    PENDING,

    /** Instalação concluída e registrada na máquina de estados. */
    COMPLETED,

    /** Falha: a instalação não foi concluída ou a máquina não aceitou a transição. */
    FAILED,
}
