package Backend.BrechoAPI;

import Backend.BrechoAPI.BrCodeChecker.FormRegime;
import Backend.BrechoAPI.BrCodeChecker.Inspection;
import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.service.PaymentGateway;
import Backend.BrechoAPI.service.PaymentGateway.PixCharge;
import Backend.BrechoAPI.service.impl.FakePixPaymentGateway;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
































































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FormaDoArtefatoPagavelTest {

    












































































































    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    

    


















    
    private static final String VETOR_ESTATICO_2_6_3 =
            "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000"
                    + "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D";

    
    private static final String VETOR_DINAMICO_2_7_2 =
            "00020101021226700014br.gov.bcb.pix2548pix.example.com/"
                    + "8b3da2f39a4140d1a91abd93113bd4415204000053039865802BR5913Fulano de Tal"
                    + "6008BRASILIA62070503***630464E4";

    







    private static final String VETOR_COMPOSTO_2_8_5_1 =
            "00020126180014br.gov.bcb.pix5204000053039865802BR5913Fulano de Tal"
                    + "6008BRASILIA62070503***80740014br.gov.bcb.pix2552pix.example.com/rec/"
                    + "2353c790eefb11eaadc10242ac1200026304F2DA";

    






    private static final int LIMITE_DO_REFERENCE_LABEL_ESTATICO = 25;

    

    
    private final PaymentGateway gateway = new FakePixPaymentGateway();

    
    private static final Instant VENCIMENTO = Instant.parse("2026-10-05T18:30:00Z");

    private static final BigDecimal VALOR = new BigDecimal("199.90");

    private static final UUID PEDIDO = UUID.fromString("7b1e9c40-0000-4000-8000-000000000008");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    

    















    @Test
    void oVetorConhecidoDoManualDoPixPassaNoConferidor() {
        Inspection estatico = emDominio(VETOR_ESTATICO_2_6_3, "o vetor §2.6.3 do manual");
        assertThat(estatico.checksumMatches())
                .as("o manual imprime 0x1D3D para este payload; se o conferidor discorda, "
                        + "a aritmética dele está errada e nada mais aqui vale — %s",
                        estatico.checksumComplaint())
                .isTrue();
        assertThat(estatico.formRegime())
                .as("§2.6.3 é o exemplo de QR estático: nenhum campo 25 de primeiro nível")
                .isEqualTo(FormRegime.STATIC_QR);

        Inspection dinamico = emDominio(VETOR_DINAMICO_2_7_2, "o vetor §2.7.2 do manual");
        assertThat(dinamico.checksumMatches())
                .as("o manual imprime 0x64E4 para este payload — %s", dinamico.checksumComplaint())
                .isTrue();
        assertThat(dinamico.formRegime())
                .as("§2.7.2 é o exemplo de QR dinâmico: campo 25 (URL do payload) presente")
                .isEqualTo(FormRegime.DYNAMIC_QR);

        Inspection composto = emDominio(VETOR_COMPOSTO_2_8_5_1, "o vetor §2.8.5.1 do manual");
        assertThat(composto.checksumMatches())
                .as("o manual imprime 0xF2DA para este payload — %s", composto.checksumComplaint())
                .isTrue();
        assertThat(composto.formRegime())
                .as("o 26 deste vetor tem só o GUI: nem chave Pix (26-01) nem URL (26-25). "
                        + "Nenhum dos dois literais o governa, e o conferidor diz isso em vez "
                        + "de escolher um por omissão")
                .isEqualTo(FormRegime.UNDETERMINED);
    }

    








    @Test
    void oVetorDoManualComUmCaractereTrocadoFalhaNoCrc() {
        String mutado = VETOR_ESTATICO_2_6_3.replace("Fulano de Tal", "Gulano de Tal");

        assertThat(mutado)
                .as("a mutação tem de mudar o payload e manter o tamanho — senão ela muda o TLV")
                .isNotEqualTo(VETOR_ESTATICO_2_6_3)
                .hasSameSizeAs(VETOR_ESTATICO_2_6_3);

        Inspection inspecao = BrCodeChecker.inspect(mutado);

        assertThat(inspecao.inDomain())
                .as("um caractere trocado DENTRO de um valor não quebra o TLV: a falha tem de "
                        + "ser de CRC, não de domínio — senão R5 e R3 provariam a mesma coisa")
                .isTrue();
        assertThat(inspecao.checksumMatches())
                .as("o payload ainda declara 1D3D e já não vale 1D3D — se isto passa, o "
                        + "conferidor não confere CRC nenhum. %s", inspecao.checksumComplaint())
                .isFalse();
    }

    















    @Test
    void oRegimeVemDoSubCampoDo26NaoDePrimeiroNivelNemDeSubstring() {
        assertThat(VETOR_DINAMICO_2_7_2)
                .as("premissa: a URL do vetor dinâmico está lá, e com o id 25 junto")
                .contains("2548pix.example.com/");
        assertThat(VETOR_COMPOSTO_2_8_5_1)
                .as("premissa: o vetor composto também contém a sequência \"25\"")
                .contains("2552pix.example.com/rec/");

        Inspection dinamico = emDominio(VETOR_DINAMICO_2_7_2, "o vetor §2.7.2 do manual");
        Inspection composto = emDominio(VETOR_COMPOSTO_2_8_5_1, "o vetor §2.8.5.1 do manual");

        assertThat(dinamico.tagIds())
                .as("no vetor dinâmico publicado, o 25 NÃO é campo de primeiro nível: ele é "
                        + "sub-campo do 26 (Merchant Account Information)")
                .doesNotContain(BrCodeChecker.URL_SUBTAG);
        assertThat(dinamico.subTag(BrCodeChecker.MERCHANT_ACCOUNT_TAG, BrCodeChecker.URL_SUBTAG))
                .as("é aqui que a URL mora, e é isto que o conferidor tem de olhar")
                .isEqualTo("pix.example.com/8b3da2f39a4140d1a91abd93113bd441");

        assertThat(composto.tagIds())
                .as("o 25 do vetor composto mora dentro do 80 (UnreservedTemplates)")
                .contains("80")
                .doesNotContain(BrCodeChecker.URL_SUBTAG);
        assertThat(composto.formRegime())
                .as("um conferidor que decidisse por contains(\"25\") chamaria este payload de "
                        + "dinâmico e aplicaria a ele um literal que não o governa")
                .isNotEqualTo(FormRegime.DYNAMIC_QR);
    }

    





    @Test
    void umaUrlHospedadaFicaForaDoDominioEmVezDePassar() {
        String urlDoFornecedor = "https://pay.fornecedor.example.com/charge/9f2c1b40-aaaa-bbbb";

        Inspection inspecao = BrCodeChecker.inspect(urlDoFornecedor);

        assertThat(inspecao.domain())
                .as("uma URL não é BR Code: o conferidor tem de dizer isso, não aprovar")
                .isEqualTo(BrCodeChecker.Domain.OUT_OF_DOMAIN);
        assertThat(inspecao.outOfDomainReason())
                .as("e o motivo tem de ser legível por quem for ler o relatório da falha")
                .contains("fora de domínio")
                .contains("parse TLV");
        assertThat(inspecao.checksumMatches())
                .as("fora de domínio NUNCA pode responder \"o CRC confere\" — não há campo 63")
                .isFalse();
    }

    




    @Test
    void sobraDepoisDoUltimoCampoFicaForaDoDominio() {
        Inspection inspecao = BrCodeChecker.inspect(VETOR_ESTATICO_2_6_3 + "lixo");

        assertThat(inspecao.domain())
                .as("o TLV tem de consumir a string inteira, sem sobra")
                .isEqualTo(BrCodeChecker.Domain.OUT_OF_DOMAIN);
        assertThat(inspecao.outOfDomainReason())
                .as("o motivo distingue sobra de identificador inválido")
                .contains("parse TLV");
    }

    

    




    @Test
    void oPayloadDaPortaEstaNoDominioDoConferidor() {
        Inspection inspecao = emDominio(payloadDaPorta(), "o payload da porta");

        assertThat(inspecao.tagIds())
                .as("a ordem do BR Code: indicador de formato primeiro, CRC por último")
                .startsWith("00")
                .endsWith("63");
    }

    









    @Test
    void oCrcDoPayloadDaPortaConfere() {
        Inspection inspecao = emDominio(payloadDaPorta(), "o payload da porta");

        assertThat(inspecao.checksumMatches())
                .as("CRC16 é a única parte do BR Code que um app de banco confere antes de "
                        + "tudo: errado, o código não é nem lido. %s", inspecao.checksumComplaint())
                .isTrue();
    }

    















    @Test
    void aReferenciaQueAtravessaAPortaEhOReferenceLabelDoArtefato() {
        PixCharge cobranca = gateway.createPixCharge(PEDIDO, VALOR, VENCIMENTO);
        Inspection inspecao = emDominio(cobranca.copyPaste(), "o payload da porta");
        String referenceLabel = referenceLabelDe(inspecao);

        assertThat(cobranca.txid())
                .as("a referência que atravessa a porta × o ReferenceLabel (62-05) do "
                        + "artefato: no QR estático são o MESMO valor, byte a byte. Se "
                        + "divergirem, o cliente paga um código que aponta para uma "
                        + "referência que o nosso lado não guardou")
                .isEqualTo(referenceLabel);
    }

    





    @Test
    void oAlfabetoDaReferenciaNoArtefatoEhAlfanumerico() {
        Inspection inspecao = emDominio(payloadDaPorta(), "o payload da porta");
        String referenceLabel = referenceLabelDe(inspecao);

        assertThat(referenceLabel)
                .as("os dois regimes do manual aceitam A-Za-z0-9 e nada mais no 62-05; "
                        + "um caractere fora disso quebra a leitura em app de banco")
                .matches("[A-Za-z0-9]+");
    }

    




















    @Test
    void aFormaEOComprimentoDaReferenciaSaoCoerentes() {
        Inspection inspecao = emDominio(payloadDaPorta(), "o payload da porta");
        String referenceLabel = referenceLabelDe(inspecao);

        switch (inspecao.formRegime()) {
            case STATIC_QR -> assertThat(referenceLabel.length())
                    .as("forma de QR ESTÁTICO (o 26 traz a chave Pix em 26-01 e nenhuma URL em "
                            + "26-25) com 62-05 de %d caracteres (\"%s\"). §2.6.2 do manual do "
                            + "Pix limita o ReferenceLabel estático a %d. Os 26–35 de §5.3.1 "
                            + "governam o txid da cobrança criada via API, que é o regime que "
                            + "ESTE payload não declara — logo o defeito não é \"comprimento "
                            + "errado\", é o produtor misturando dois regimes: forma de um, "
                            + "comprimento do outro (conflitos/arquiteto-regime-do-txid.md, "
                            + "dono: Dev). Campos de primeiro nível lidos: %s",
                            referenceLabel.length(), referenceLabel,
                            LIMITE_DO_REFERENCE_LABEL_ESTATICO, inspecao.tagIds())
                    .isLessThanOrEqualTo(LIMITE_DO_REFERENCE_LABEL_ESTATICO);

            case DYNAMIC_QR -> fail(("o produtor passou a emitir QR DINÂMICO (URL em 26-25) e"
                    + " esta casa não tem régua escrita de comprimento para o 62-05 dinâmico: a"
                    + " metade dinâmica da tabela D8 da ADR 0013 (\"26–35\") é contradita pelo"
                    + " vetor §2.7.2 do próprio manual, onde o 62-05 vale \"***\" porque a"
                    + " referência mora na URL da cobrança. Quem redige é o Arquiteto; este"
                    + " teste não aprova mudança de regime sem oráculo. 62-05 encontrado:"
                    + " \"%s\" (%d caracteres)").formatted(referenceLabel, referenceLabel.length()));

            case UNDETERMINED -> fail(("o 26 do payload não traz nem chave Pix (26-01) nem URL"
                    + " (26-25): nenhum dos dois literais do manual governa este artefato, logo"
                    + " não há limite a aplicar ao 62-05 — e o conferidor não escolhe regime por"
                    + " omissão. Campos de primeiro nível: %s; 62-05 encontrado: \"%s\"")
                    .formatted(inspecao.tagIds(), referenceLabel));
        }
    }

    












    @Test
    void pedidosDiferentesNaoCompartilhamAReferenciaNoArtefato() {
        UUID umPedido = UUID.fromString("7b1e9c40-0000-4000-8000-000000000011");
        UUID outroPedido = UUID.fromString("7b1e9c40-0000-4000-8000-000000000012");

        String umaReferencia = referenceLabelDe(
                emDominio(gateway.createPixCharge(umPedido, VALOR, VENCIMENTO).copyPaste(),
                        "o payload do primeiro pedido"));
        String outraReferencia = referenceLabelDe(
                emDominio(gateway.createPixCharge(outroPedido, VALOR, VENCIMENTO).copyPaste(),
                        "o payload do segundo pedido"));

        assertThat(umaReferencia)
                .as("dois pedidos com a mesma referência dentro do artefato violariam "
                        + "uk_payments_txid na segunda gravação — a régua é nossa, do índice")
                .isNotEqualTo(outraReferencia)
                .isNotBlank();
    }

    

    










    @Test
    void oCopiaEColaDoCheckoutEstaNoDominioEOCrcConfere() throws Exception {
        String corpo = fecharPedido();
        String copyPaste = ler(corpo, "$.pix.copyPaste");

        Inspection inspecao = emDominio(copyPaste, "o $.pix.copyPaste do POST /me/orders");

        assertThat(inspecao.checksumMatches())
                .as("o que a ROTA entrega ao cliente tem de ter CRC válido — não basta o "
                        + "que a porta devolve em memória. %s", inspecao.checksumComplaint())
                .isTrue();
    }

    





    @Test
    void aReferenciaDoCheckoutEhOReferenceLabelDoCopiaECola() throws Exception {
        String corpo = fecharPedido();
        String copyPaste = ler(corpo, "$.pix.copyPaste");
        String txid = ler(corpo, "$.pix.txid");

        Inspection inspecao = emDominio(copyPaste, "o $.pix.copyPaste do POST /me/orders");

        assertThat(txid)
                .as("$.pix.txid × o ReferenceLabel (62-05) de $.pix.copyPaste: no QR estático "
                        + "são o mesmo valor, e é pela rota que o cliente recebe os dois")
                .isEqualTo(referenceLabelDe(inspecao));
    }

    

    




    private static Inspection emDominio(String payload, String origem) {
        Inspection inspecao = BrCodeChecker.inspect(payload);

        assertThat(inspecao.inDomain())
                .as("%s não é um BR Code — e o conferidor responde \"fora de domínio\", "
                        + "nunca \"passou\". Motivo: %s", origem, inspecao.outOfDomainReason())
                .isTrue();

        return inspecao;
    }

    
    private static String referenceLabelDe(Inspection inspecao) {
        String referenceLabel = inspecao.referenceLabel();

        assertThat(referenceLabel)
                .as("o artefato não tem o sub-campo 62-05 (ReferenceLabel). Campos de "
                        + "primeiro nível lidos: %s", inspecao.tagIds())
                .isNotNull();

        return referenceLabel;
    }

    private String payloadDaPorta() {
        return gateway.createPixCharge(PEDIDO, VALOR, VENCIMENTO).copyPaste();
    }

    




    private String fecharPedido() throws Exception {
        String marcador = UUID.randomUUID().toString().substring(0, 8);
        String email = "pv8-" + marcador + "@teste.local";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente %s","email":"%s","password":"senha-forte-123",
                                 "phone":"11987654321"}"""
                                .formatted(marcador, email)))
                .andExpect(status().isCreated());

        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = ler(login, "$.accessToken");

        String endereco = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01310100","street":"Rua Um","number":"100",
                                 "complement":null,"district":"Centro","city":"Cidade",
                                 "uf":"%s"}""".formatted(UfBrasileira.SP.name())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID addressId = UUID.fromString(ler(endereco, "$.id"));

        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + criarPeca(marcador) + "\"}"))
                .andExpect(status().isOk());

        return mockMvc.perform(post("/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + addressId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    
    private UUID criarPeca(String marcador) {
        User dona = userRepository.save(User.createWithRole(
                "Dona " + marcador, "dona-pv8-" + marcador + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + marcador, "cat-pv8-" + marcador, CategorySizeType.ALPHA));
        Product peca = productRepository.save(Product.create(dona, categoria, "Peça " + marcador,
                "descrição", "Marca", "M", null, "azul", ProductCondition.GOOD,
                new BigDecimal("70.00")));

        return peca.getId();
    }

    

    private static String ler(String corpo, String caminho) {
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    private static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);
}
