package Backend.BrechoAPI;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;






























public class LimpezaDeDdlDeRitualExtension implements AfterEachCallback {

    





    static volatile boolean ativa = true;

    private static Connection conexao;

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (!ativa) {
            return;
        }
        PostgreSQLContainer<?> container = BancoDeTesteCompartilhado.seIniciado();
        if (container == null) {
            return;
        }
        varrer(container);
    }

    





    static int varrer(PostgreSQLContainer<?> container) throws Exception {
        Connection c = conexao(container);
        int removidos = 0;
        removidos += executarComandosDe(c,
                "SELECT 'ALTER TABLE ' || conrelid::regclass || ' DROP CONSTRAINT IF EXISTS \"'"
                        + " || conname || '\"' FROM pg_constraint WHERE conname LIKE 'qa\\_%'");
        removidos += executarComandosDe(c,
                "SELECT 'DROP INDEX IF EXISTS \"' || schemaname || '\".\"' || indexname || '\"'"
                        + " FROM pg_indexes WHERE indexname LIKE 'qa\\_%'");
        removidos += executarComandosDe(c,
                "SELECT 'DROP SCHEMA IF EXISTS \"' || nspname || '\" CASCADE'"
                        + " FROM pg_namespace WHERE nspname LIKE 'qa\\_%'");
        return removidos;
    }

    private static int executarComandosDe(Connection c, String selectQueGeraOsComandos)
            throws Exception {
        List<String> comandos = new ArrayList<>();
        try (Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(selectQueGeraOsComandos)) {
            while (rs.next()) {
                comandos.add(rs.getString(1));
            }
        }
        try (Statement s = c.createStatement()) {
            for (String comando : comandos) {
                s.execute(comando);
            }
        }
        return comandos.size();
    }

    private static Connection conexao(PostgreSQLContainer<?> container) throws Exception {
        if (conexao == null || conexao.isClosed()) {
            conexao = container.createConnection("");
        }
        return conexao;
    }
}
