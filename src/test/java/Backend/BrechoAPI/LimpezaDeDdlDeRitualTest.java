package Backend.BrechoAPI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


























@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LimpezaDeDdlDeRitualTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired JdbcTemplate jdbc;

    private String controleParaLimpar;

    @Test
    void ddlDeRitualAutoCommitaESobreviveSemLimpeza_eAVarreduraORemove() throws Exception {
        String nome = "qa_prova_" + UUID.randomUUID().toString().substring(0, 8);

        jdbc.execute("ALTER TABLE addresses ADD CONSTRAINT " + nome + " CHECK (true) NOT VALID");

        assertThat(constraintExiste(nome))
                .as("o MECANISMO: DDL em Postgres é auto-commit — a constraint '%s' plantada "
                        + "FICA no container compartilhado, sem depender de rollback de transação "
                        + "nenhuma. É exatamente o que vaza quando um desfazer de ritual não roda "
                        + "(COLHEITA 55).", nome)
                .isTrue();

        int removidos = LimpezaDeDdlDeRitualExtension.varrer(TestcontainersConfiguration.POSTGRES);

        assertThat(removidos)
                .as("a varredura executou ao menos o DROP da constraint plantada")
                .isGreaterThanOrEqualTo(1);
        assertThat(constraintExiste(nome))
                .as("a CURA: depois da varredura, o qa_* vazado não existe mais — o vazamento "
                        + "não atravessa a fronteira do teste. Se isto ficar verdadeiro, a "
                        + "varredura não removeu nada (varrer() quebrada).")
                .isFalse();
    }

    @Test
    void aVarreduraNaoDerrubaObjetoSemOPrefixoQa() throws Exception {
        String controle = "zz_controle_" + UUID.randomUUID().toString().substring(0, 8);
        controleParaLimpar = controle;

        jdbc.execute("ALTER TABLE addresses ADD CONSTRAINT " + controle + " CHECK (true) NOT VALID");

        LimpezaDeDdlDeRitualExtension.varrer(TestcontainersConfiguration.POSTGRES);

        assertThat(constraintExiste(controle))
                .as("CONTROLE de escopo: a varredura só derruba 'qa_*'. Este objeto não tem o "
                        + "prefixo e TEM de sobreviver — se cair, a varredura deixou de ser "
                        + "escopada e estaria destruindo schema legítimo calada.")
                .isTrue();
    }

    @AfterEach
    void limparControle() {
        if (controleParaLimpar != null) {
            jdbc.execute("ALTER TABLE addresses DROP CONSTRAINT IF EXISTS " + controleParaLimpar);
            controleParaLimpar = null;
        }
    }

    private boolean constraintExiste(String nome) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = ?", Integer.class, nome);
        return n != null && n > 0;
    }
}
