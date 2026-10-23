package Backend.BrechoAPI;

import Backend.BrechoAPI.service.PaymentGateway;
import Backend.BrechoAPI.service.PaymentGateway.PixCharge;
import Backend.BrechoAPI.service.impl.FakePixPaymentGateway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


































class GatewayFalsoTest {

    






































































































    



    private final PaymentGateway gateway = new FakePixPaymentGateway();

    
    private static final Instant VENCIMENTO = Instant.parse("2026-09-05T18:30:00Z");

    private static final BigDecimal VALOR = new BigDecimal("199.90");

    








    @Test
    void duasChamadasComOMesmoPedidoDevolvemAMesmaCobranca() {
        UUID orderId = UUID.fromString("2c3d0f4a-1111-4000-8000-000000000001");

        PixCharge primeira = gateway.createPixCharge(orderId, VALOR, VENCIMENTO);
        PixCharge segunda = gateway.createPixCharge(orderId, VALOR, VENCIMENTO);

        assertThat(segunda.txid())
                .as("o txid é a referência da cobrança no gateway: mesmo pedido, mesma cobrança")
                .isEqualTo(primeira.txid());
        assertThat(segunda.copyPaste())
                .as("o payload copiável também é o mesmo — senão o cliente fica com dois códigos vivos")
                .isEqualTo(primeira.copyPaste());
    }

    




    @Test
    void pedidosDiferentesRecebemCobrancasDiferentes() {
        UUID umPedido = UUID.fromString("2c3d0f4a-1111-4000-8000-000000000001");
        UUID outroPedido = UUID.fromString("2c3d0f4a-1111-4000-8000-000000000002");

        PixCharge cobrancaDeUm = gateway.createPixCharge(umPedido, VALOR, VENCIMENTO);
        PixCharge cobrancaDoOutro = gateway.createPixCharge(outroPedido, VALOR, VENCIMENTO);

        assertThat(cobrancaDoOutro.txid())
                .as("dois pedidos não podem reivindicar a mesma cobrança — a V3 tem "
                        + "uk_payments_txid dizendo o mesmo em banco")
                .isNotEqualTo(cobrancaDeUm.txid());
        assertThat(cobrancaDoOutro.copyPaste())
                .as("pagar o código de um pedido não pode quitar o outro")
                .isNotEqualTo(cobrancaDeUm.copyPaste());
    }

    









    @Test
    void copiaEColaNaoEhVazio() {
        UUID orderId = UUID.fromString("2c3d0f4a-1111-4000-8000-000000000003");

        PixCharge cobranca = gateway.createPixCharge(orderId, VALOR, VENCIMENTO);

        assertThat(cobranca.txid())
                .as("txid em branco é cobrança sem referência: nada casa o pagamento com o pedido")
                .isNotBlank();
        assertThat(cobranca.copyPaste())
                .as("copia-e-cola em branco passa por notNull e não é copiável por ninguém")
                .isNotBlank();
        assertThat(cobranca.copyPaste())
                .as("o payload copiável não é a referência — são campos diferentes, com donos diferentes")
                .isNotEqualTo(cobranca.txid());
    }

    









    @Test
    void instanciaNovaDoGatewayDevolveAMesmaCobranca() {
        UUID orderId = UUID.fromString("2c3d0f4a-1111-4000-8000-000000000004");

        PixCharge antesDoRestart = gateway.createPixCharge(orderId, VALOR, VENCIMENTO);
        PixCharge depoisDoRestart =
                new FakePixPaymentGateway().createPixCharge(orderId, VALOR, VENCIMENTO);

        assertThat(depoisDoRestart.txid())
                .as("o txid é DERIVADO do orderId; se veio de mapa em memória, "
                        + "um restart entre a transação 1 e a retomada cobra o cliente de novo")
                .isEqualTo(antesDoRestart.txid());
        assertThat(depoisDoRestart.copyPaste())
                .as("o payload segue o txid: derivado, não guardado")
                .isEqualTo(antesDoRestart.copyPaste());
    }
}
