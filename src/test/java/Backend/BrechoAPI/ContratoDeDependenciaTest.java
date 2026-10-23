package Backend.BrechoAPI;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;



























class ContratoDeDependenciaTest {

    private static final int LIMITE_DECLARADO_NO_DTO = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    
    @Test
    void bcryptAceitaExatamenteOLimiteQueODtoDeclara() {
        String senha = "a".repeat(LIMITE_DECLARADO_NO_DTO);
        assertThat(bytes(senha)).isEqualTo(LIMITE_DECLARADO_NO_DTO);

        assertThatCode(() -> {
            String hash = encoder.encode(senha);
            assertThat(encoder.matches(senha, hash)).isTrue();
        }).doesNotThrowAnyException();
    }

    



    @Test
    void bcryptLancaUmByteAcimaDoLimiteEmVezDeTruncar() {
        String senha = "a".repeat(LIMITE_DECLARADO_NO_DTO + 1);

        assertThatThrownBy(() -> encoder.encode(senha))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");
    }

    




    @Test
    void oLimiteEhEmBytesNaoEmCaracteres() {
        String senha = "á".repeat(40);

        assertThat(senha.length()).isEqualTo(40);
        assertThat(bytes(senha)).isEqualTo(80);

        assertThatThrownBy(() -> encoder.encode(senha))
                .isInstanceOf(IllegalArgumentException.class);
    }

    




    @Test
    void oLimiteDoDtoCoincideComOMaximoRealDoBcrypt() {
        assertThatCode(() -> encoder.encode("a".repeat(LIMITE_DECLARADO_NO_DTO)))
                .as("o limite do DTO deveria ser aceito")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> encoder.encode("a".repeat(LIMITE_DECLARADO_NO_DTO + 1)))
                .as("o limite do DTO deveria ser o MÁXIMO, não um valor abaixo dele")
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
