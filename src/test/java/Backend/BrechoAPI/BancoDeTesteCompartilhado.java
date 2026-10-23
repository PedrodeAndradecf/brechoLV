package Backend.BrechoAPI;

import java.util.concurrent.atomic.AtomicReference;
import org.testcontainers.containers.PostgreSQLContainer;














final class BancoDeTesteCompartilhado {

    private static final AtomicReference<PostgreSQLContainer<?>> REF = new AtomicReference<>();

    private BancoDeTesteCompartilhado() {
    }

    static void registrar(PostgreSQLContainer<?> container) {
        REF.set(container);
    }

    
    static PostgreSQLContainer<?> seIniciado() {
        PostgreSQLContainer<?> container = REF.get();
        return (container != null && container.isRunning()) ? container : null;
    }
}
