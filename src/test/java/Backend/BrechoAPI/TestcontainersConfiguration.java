package Backend.BrechoAPI;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;











































@Configuration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    static final PostgresSingleton POSTGRES = iniciarSingleton();

    private static PostgresSingleton iniciarSingleton() {
        PostgresSingleton container = new PostgresSingleton();
        container.start();
        BancoDeTesteCompartilhado.registrar(container);
        return container;
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }

    




    static final class PostgresSingleton extends PostgreSQLContainer<PostgresSingleton> {
        PostgresSingleton() {
            super(DockerImageName.parse("postgres:16-alpine"));
            
            
            
            
            
            
            withCommand("postgres", "-c", "max_connections=500");
        }

        @Override
        public void start() {
            
            
            
            
            
            if (!isRunning()) {
                super.start();
            }
        }

        @Override
        public void stop() {
            
            
        }
    }
}
