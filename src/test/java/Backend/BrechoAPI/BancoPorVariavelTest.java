package Backend.BrechoAPI;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;






































































class BancoPorVariavelTest {

    
    private static final String JWT_SECRET_DE_TESTE = "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=";

    










































































    
















    @Test
    void aUrlDoDatasourceEmpacotadaTemOHostPorVariavel() throws IOException {
        Properties empacotado = new Properties();
        try (InputStream in = BrechoApiApplication.class.getResourceAsStream("/application.properties")) {
            assertThat(in)
                    .as("application.properties precisa estar no classpath (é o recurso empacotado)")
                    .isNotNull();
            empacotado.load(in);
        }

        String url = empacotado.getProperty("spring.datasource.url");
        assertThat(url)
                .as("spring.datasource.url precisa existir no application.properties empacotado "
                        + "— apagar a linha não é tirar o literal")
                .isNotBlank();

        assertThat(url)
                .as("spring.datasource.url não pode ter o host literal 'localhost' (decisão do Pedro "
                        + "07/10-b item 3: variável de ambiente, nunca literal). URL lida: %s", url)
                .doesNotContain("localhost");

        assertThat(segmentoDeHost(url))
                .as("o host de spring.datasource.url deve ser exatamente ${DB_HOST} — sem default, "
                        + "que seria literal de novo. URL lida: %s", url)
                .isEqualTo("${DB_HOST}");
    }

    






    @Test
    void semServiceConnectionOContextoSobePeloDbHost() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))) {
            pg.start();

            InetAddress soPorEsteHost = ipv4NaoLoopback();
            try (Ponte ponte = Ponte.abrir(soPorEsteHost, pg.getHost(), pg.getFirstMappedPort())) {

                Map<String, Object> ambiente = new HashMap<>();
                ambiente.put("DB_HOST", soPorEsteHost.getHostAddress());
                ambiente.put("DB_PORT", String.valueOf(ponte.porta()));
                ambiente.put("DB_NAME", pg.getDatabaseName());
                ambiente.put("DB_USER", pg.getUsername());
                ambiente.put("DB_PASSWORD", pg.getPassword());
                ambiente.put("jwt.secret", JWT_SECRET_DE_TESTE);
                ambiente.put("server.port", "0");
                ambiente.put("spring.main.banner-mode", "off");

                ConfigurableApplicationContext ctx;
                try {
                    ctx = subirSemServiceConnection(ambiente);
                } catch (RuntimeException naoSubiu) {
                    throw new AssertionError("o contexto SEM @ServiceConnection não subiu com DB_HOST="
                            + soPorEsteHost.getHostAddress() + " DB_PORT=" + ponte.porta()
                            + " — o banco só é alcançável por esse host, então a URL do datasource "
                            + "não está usando DB_HOST (e DB_PORT) para chegar a ele. Causa raiz: " + causaRaiz(naoSubiu), naoSubiu);
                }

                try (ctx) {
                    assertThat(ctx.getBeanNamesForType(PostgreSQLContainer.class))
                            .as("o contexto deste teste não pode ter container de @ServiceConnection "
                                    + "(TestcontainersConfiguration vazou pela varredura)")
                            .isEmpty();

                    DataSource ds = ctx.getBean(DataSource.class);
                    try (Connection c = ds.getConnection();
                         Statement st = c.createStatement();
                         ResultSet rs = st.executeQuery("select 1")) {
                        assertThat(c.getMetaData().getURL())
                                .as("a conexão viva precisa ter saído pelo host de DB_HOST e pela porta da ponte")
                                .startsWith("jdbc:postgresql://" + soPorEsteHost.getHostAddress()
                                        + ":" + ponte.porta() + "/");
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                    assertThat(ponte.conexoesAceitas())
                            .as("o tráfego até o banco passou pela ponte")
                            .isPositive();
                }
            }
        }
    }

    

    private static ConfigurableApplicationContext subirSemServiceConnection(Map<String, Object> ambiente) {
        return new SpringApplicationBuilder(BrechoApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(ctx -> {
                    
                    ctx.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("dv7a-ambiente-do-teste", ambiente));
                    ctx.getBeanFactory().registerSingleton(SoSrcMain.class.getName(), new SoSrcMain());
                })
                .run();
    }

    
    private static String segmentoDeHost(String url) {
        String prefixo = "jdbc:postgresql://";
        assertThat(url).as("formato inesperado de spring.datasource.url: %s", url).startsWith(prefixo);
        String resto = url.substring(prefixo.length());
        int fim = resto.indexOf('/');
        String hostEPorta = fim < 0 ? resto : resto.substring(0, fim);
        
        if (hostEPorta.startsWith("${")) {
            return hostEPorta.substring(0, hostEPorta.indexOf('}') + 1);
        }
        int doisPontos = hostEPorta.indexOf(':');
        return doisPontos < 0 ? hostEPorta : hostEPorta.substring(0, doisPontos);
    }

    private static InetAddress ipv4NaoLoopback() throws SocketException {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) {
                continue;
            }
            for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                    return a;
                }
            }
        }
        throw new AssertionError("esta máquina não tem IPv4 não loopback ativo — a ponte da asserção 2 "
                + "precisa de um host que NÃO seja localhost para distinguir DB_HOST do literal");
    }

    
    private static String causaRaiz(Throwable t) {
        Throwable acima = t;
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            acima = r;
            r = r.getCause();
        }
        return acima.getMessage() + " <- " + r.getClass().getName() + ": " + r.getMessage();
    }

    





    static final class SoSrcMain extends TypeExcludeFilter {
        @Override
        public boolean match(MetadataReader leitor, MetadataReaderFactory fabrica) throws IOException {
            return leitor.getResource().getURL().toString().contains("/test-classes/");
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o.getClass() == getClass();
        }

        @Override
        public int hashCode() {
            return SoSrcMain.class.hashCode();
        }
    }

    



    static final class Ponte implements AutoCloseable {
        private final ServerSocket servidor;
        private final List<Socket> abertos = new CopyOnWriteArrayList<>();
        private volatile int aceitas;

        private Ponte(ServerSocket servidor) {
            this.servidor = servidor;
        }

        static Ponte abrir(InetAddress escutaEm, String destino, int portaDestino) throws IOException {
            ServerSocket ss = new ServerSocket();
            ss.bind(new InetSocketAddress(escutaEm, 0));
            Ponte p = new Ponte(ss);
            Thread aceitador = new Thread(() -> {
                while (!ss.isClosed()) {
                    try {
                        Socket cliente = ss.accept();
                        Socket banco = new Socket(destino, portaDestino);
                        p.aceitas++;
                        p.abertos.add(cliente);
                        p.abertos.add(banco);
                        bombear(cliente, banco);
                        bombear(banco, cliente);
                    } catch (IOException fechado) {
                        return;
                    }
                }
            }, "dv7a-ponte-aceita");
            aceitador.setDaemon(true);
            aceitador.start();
            return p;
        }

        private static void bombear(Socket de, Socket para) {
            Thread t = new Thread(() -> {
                byte[] buf = new byte[8192];
                try (InputStream in = de.getInputStream(); OutputStream out = para.getOutputStream()) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        out.write(buf, 0, n);
                        out.flush();
                    }
                } catch (IOException fim) {
                    
                } finally {
                    try {
                        para.close();
                    } catch (IOException ignorado) {
                        
                    }
                }
            }, "dv7a-ponte-bomba");
            t.setDaemon(true);
            t.start();
        }

        int porta() {
            return servidor.getLocalPort();
        }

        int conexoesAceitas() {
            return aceitas;
        }

        @Override
        public void close() throws IOException {
            servidor.close();
            for (Socket s : abertos) {
                s.close();
            }
        }
    }
}
