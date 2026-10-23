package Backend.BrechoAPI;

import Backend.BrechoAPI.exception.PaymentGatewayException;
import Backend.BrechoAPI.service.PaymentGateway;
import Backend.BrechoAPI.service.impl.FakePixPaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;











































final class PortaDeCobrancaControlavel implements PaymentGateway {

    

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        @Primary
        PortaDeCobrancaControlavel portaDeCobrancaControlavel() {
            return new PortaDeCobrancaControlavel();
        }
    }

    enum Modo { NORMAL, DISTINTO, FALHA_CERTA, FALHA_CRUA }

    
    static final String SEGREDO_DA_CAUSA =
            "read timed out: gateway.interno.local:8443 token=dv0c-segredo";

    



    static final String MENSAGEM_DA_FALHA_CERTA =
            "não foi possível gerar a cobrança Pix agora; seu pedido está registrado e você pode"
                    + " tentar de novo em instantes";

    static final long TETO_DA_PORTA_SEGUNDOS = 60;
    static final String TETO_DA_PORTA = TETO_DA_PORTA_SEGUNDOS + " s";

    
    record Chamada(int numero, UUID orderId, BigDecimal amount, Instant expiresAt, Modo modo,
                   PixCharge artefato, boolean segurada) {}

    private final PaymentGateway real = new FakePixPaymentGateway();
    private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
    private final AtomicInteger numeracao = new AtomicInteger();
    private final AtomicInteger distintos = new AtomicInteger();
    private final AtomicInteger segurandoAgora = new AtomicInteger();
    private final AtomicInteger devolvidas = new AtomicInteger();

    private volatile Modo modo = Modo.NORMAL;
    private volatile IntPredicate segurar = k -> false;
    private volatile CountDownLatch liberacao = new CountDownLatch(1);

    @Override
    public PixCharge createPixCharge(UUID orderId, BigDecimal amount, Instant expiresAt) {
        int numero = numeracao.incrementAndGet();
        Modo modoDaChamada = modo;
        PixCharge artefato = modoDaChamada == Modo.DISTINTO
                ? distinto(orderId)
                : real.createPixCharge(orderId, amount, expiresAt);
        boolean segura = segurar.test(numero);

        chamadas.add(new Chamada(numero, orderId, amount, expiresAt, modoDaChamada, artefato, segura));

        if (segura) {
            CountDownLatch latch = liberacao;
            segurandoAgora.incrementAndGet();
            try {
                latch.await(TETO_DA_PORTA_SEGUNDOS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                segurandoAgora.decrementAndGet();
            }
        }

        devolvidas.incrementAndGet();
        return switch (modoDaChamada) {
            case NORMAL, DISTINTO -> artefato;
            case FALHA_CERTA -> throw new PaymentGatewayException(MENSAGEM_DA_FALHA_CERTA,
                    new IOException(SEGREDO_DA_CAUSA));
            case FALHA_CRUA -> throw new IllegalStateException(SEGREDO_DA_CAUSA);
        };
    }

    

    private PixCharge distinto(UUID orderId) {
        int n = distintos.incrementAndGet();
        String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return new PixCharge("DIST" + n + sufixo, "DISTINTO-" + n + "-" + sufixo + "-" + orderId);
    }

    

    

    void reiniciar() {
        liberacao.countDown();
        liberacao = new CountDownLatch(1);
        segurar = k -> false;
        modo = Modo.NORMAL;
        chamadas.clear();
        numeracao.set(0);
        devolvidas.set(0);
    }

    void modo(Modo novo) {
        this.modo = novo;
    }

    

    void armar(Modo novo, IntPredicate quaisSegurar) {
        this.modo = novo;
        this.segurar = quaisSegurar;
    }

    void liberar() {
        liberacao.countDown();
    }

    

    List<Chamada> chamadas() {
        return List.copyOf(chamadas);
    }

    List<Chamada> chamadasPara(UUID orderId) {
        return chamadas.stream().filter(c -> c.orderId().equals(orderId)).toList();
    }

    int entradas() {
        return chamadas.size();
    }

    int segurandoAgora() {
        return segurandoAgora.get();
    }

    int devolvidas() {
        return devolvidas.get();
    }
}
