package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Address;
import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.domain.ShippingSnapshot;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.OrderStatus;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.dto.response.OrderResponseDTO;
import Backend.BrechoAPI.service.CheckoutService;
import Backend.BrechoAPI.service.CheckoutTransactionService;
import Backend.BrechoAPI.service.CheckoutTransactionService.CheckoutOutcome;
import Backend.BrechoAPI.service.PaymentGateway;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;












































































class ArtefatoGuardadoSemReferenciaTest {

    



    static final String SENTINELA = "SENTINELA-DV0-A::artefato-guardado-sem-referencia::nao-e-BR-Code";

    private static final Instant AGORA = Instant.parse("2026-10-07T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    

    







    @Test
    void checkoutOutcomeComArtefatoESemReferenciaTemCobranca() {
        CheckoutOutcome outcome = outcome(null, SENTINELA);

        assertThat(outcome.hasCharge())
                .as("DV0-a / ADR 0015 D-1: o predicado é 'artefato guardado presente', NUNCA a "
                        + "referência. Com pixCopyPaste presente e txid nulo, hasCharge() tem de "
                        + "ser TRUE — false aqui é o predicado ainda lendo txid, e a volta ao "
                        + "checkout chamaria o fornecedor de novo")
                .isTrue();
    }

    




    @Test
    void withChargeNaoExigeAReferencia() {
        CheckoutOutcome semNada = outcome(null, null);

        assertThatCode(() -> semNada.withCharge(null, SENTINELA))
                .as("DV0-a / ADR 0015 D-1: withCharge deixa de exigir txid — a referência do "
                        + "fornecedor é legitimamente nula (0013 D6), o artefato não")
                .doesNotThrowAnyException();

        CheckoutOutcome comArtefato = semNada.withCharge(null, SENTINELA);
        assertThat(comArtefato.pixCopyPaste())
                .as("o artefato atravessa withCharge INTACTO — é o mesmo, não um equivalente")
                .isEqualTo(SENTINELA);
        assertThat(comArtefato.hasCharge())
                .as("e o outcome que sai de withCharge com artefato TEM cobrança")
                .isTrue();
    }

    












    @Test
    void paymentComArtefatoESemReferenciaTemCobranca() throws Exception {
        Payment payment = paymentHidratado(null, SENTINELA);

        assertThat(payment.hasCharge())
                .as("DV0-a / ADR 0015 D-1: Payment.hasCharge() lê o ARTEFATO (pix_copy_paste), "
                        + "não a referência. Os dois hasCharge() divergindo é a divergência "
                        + "silenciosa que a ADR nomeia: mudar um só não fecha o item")
                .isTrue();
    }

    

    













    @Test
    void retomadaDeArtefatoSemReferenciaNaoChamaAPortaEDevolveOGuardado() {
        CheckoutTransactionService transacional = mock(CheckoutTransactionService.class);
        when(transacional.createOrder(any(), any(), any()))
                .thenReturn(outcome(null, SENTINELA, true));
        PortaQueConta porta = new PortaQueConta();

        CheckoutService.CheckoutResult resultado =
                new CheckoutService(transacional, porta, RELOGIO)
                        .checkout(UUID.randomUUID(), UUID.randomUUID());

        assertThat(porta.chamadas)
                .as("DV0-a, caso 2 — A VOLTA AO CHECKOUT NÃO PODE TOCAR A PORTA quando há artefato "
                        + "guardado, MESMO COM A REFERÊNCIA NULA. Uma chamada aqui é o predicado "
                        + "decidindo por txid: sob link hospedado o txid é nulo para sempre, e "
                        + "TODA volta à tela cria um artefato novo lá fora")
                .isEmpty();
        verify(transacional, never()).attachCharge(any(), any());

        OrderResponseDTO resposta = resultado.order();
        assertThat(resposta.pix())
                .as("o pedido tem artefato guardado: a resposta tem de trazê-lo, não pix nulo")
                .isNotNull();
        assertThat(resposta.pix().copyPaste())
                .as("sentinela esperado × encontrado: a resposta tem de carregar O ARTEFATO "
                        + "GUARDADO, não um regerado nem o que a porta devolveria")
                .isEqualTo(SENTINELA);
        assertThat(resultado.resumed()).as("é retomada (200), não pedido novo").isTrue();
    }

    

    
























    @Test
    void semArtefatoNenhumOPredicadoEhFalsoEAPortaEhChamada() throws Exception {
        assertThat(outcome(null, null).hasCharge())
                .as("controle: CheckoutOutcome sem artefato e sem referência NÃO tem cobrança")
                .isFalse();
        assertThat(paymentHidratado(null, null).hasCharge())
                .as("controle: Payment sem artefato e sem referência NÃO tem cobrança")
                .isFalse();

        CheckoutTransactionService transacional = mock(CheckoutTransactionService.class);
        when(transacional.createOrder(any(), any(), any())).thenReturn(outcome(null, null));
        PortaQueConta porta = new PortaQueConta();

        CheckoutService.CheckoutResult resultado =
                new CheckoutService(transacional, porta, RELOGIO)
                        .checkout(UUID.randomUUID(), UUID.randomUUID());

        assertThat(porta.chamadas)
                .as("controle: pedido sem artefato tem de chamar a porta UMA vez — zero aqui é "
                        + "um predicado que responde 'há artefato' para tudo")
                .hasSize(1);
        assertThat(resultado.order().pix().copyPaste())
                .as("e a resposta carrega o que a porta acabou de devolver")
                .isEqualTo(PortaQueConta.ARTEFATO_DA_PORTA);
    }

    

    

    static final class PortaQueConta implements PaymentGateway {
        static final String ARTEFATO_DA_PORTA = "ARTEFATO-REGERADO-PELA-PORTA";
        final List<UUID> chamadas = new CopyOnWriteArrayList<>();

        @Override
        public PixCharge createPixCharge(UUID orderId, BigDecimal amount, Instant expiresAt) {
            chamadas.add(orderId);
            return new PixCharge("REF-DA-PORTA-" + orderId, ARTEFATO_DA_PORTA);
        }
    }

    private static final UUID PEDIDO = UUID.fromString("d0a00000-0000-4000-8000-00000000000a");

    private static CheckoutOutcome outcome(String txid, String pixCopyPaste) {
        return outcome(txid, pixCopyPaste, false);
    }

    private static CheckoutOutcome outcome(String txid, String pixCopyPaste, boolean retomado) {
        return new CheckoutOutcome(PEDIDO, OrderStatus.AWAITING_PAYMENT,
                new BigDecimal("70.00"), new BigDecimal("20.00"), new BigDecimal("90.00"),
                AGORA.plus(Duration.ofMinutes(30)), endereco(),
                List.of(new CheckoutOutcome.Item(UUID.randomUUID(), "Peça", new BigDecimal("70.00"))),
                txid, pixCopyPaste, retomado);
    }

    private static ShippingSnapshot endereco() {
        User cliente = User.createClient("Cliente DV0-a", "dv0a@teste.local", "hash", "11987654321");
        Address endereco = Address.create(cliente, "01310100", "Rua Um", "100", null, "Centro",
                "São Paulo", UfBrasileira.SP);
        return ShippingSnapshot.copyOf(endereco, cliente);
    }

    


    private static Payment paymentHidratado(String txid, String pixCopyPaste) throws Exception {
        Constructor<Payment> construtor = Payment.class.getDeclaredConstructor();
        construtor.setAccessible(true);
        Payment payment = construtor.newInstance();
        campo("txid").set(payment, txid);
        campo("pixCopyPaste").set(payment, pixCopyPaste);
        return payment;
    }

    private static Field campo(String nome) throws NoSuchFieldException {
        Field campo = Payment.class.getDeclaredField(nome);
        campo.setAccessible(true);
        return campo;
    }

    




























































































































}
