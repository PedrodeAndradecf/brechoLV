package Backend.BrechoAPI.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


































@ConfigurationProperties("brecho")
public record AmbienteDeExecucao(String ambiente) {

    
    private static final String DESENVOLVIMENTO = "desenvolvimento";

    public AmbienteDeExecucao {
        if (ambiente != null && !ambiente.isEmpty() && !DESENVOLVIMENTO.equals(ambiente)) {
            throw new IllegalStateException(
                    "brecho.ambiente=\"" + ambiente + "\" não é um valor reconhecido — o único"
                            + " valor com efeito é \"" + DESENVOLVIMENTO + "\" (exato, sensível a"
                            + " caixa); ausente ou vazio é produção. Valor"
                            + " desconhecido recusa subir em vez de cair em produção calado —"
                            + " corrija a declaração de brecho.ambiente, ou remova-a para rodar"
                            + " como produção");
        }
    }

    
    public boolean isDesenvolvimento() {
        return DESENVOLVIMENTO.equals(ambiente);
    }
}
