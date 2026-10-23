package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;















































class CobrancaCongeladaTest {

    private static final BigDecimal AMOUNT_ORIGINAL = new BigDecimal("119.80");
    private static final String NSU_ORIGINAL = "nsu-f2-original";

    

    private static final List<String> PALAVRAS = List.of("amount", "valor", "total", "price",
            "preco", "nsu");

    @Test
    void f2_paymentGuardaAmountEOrderNsuENenhumMetodoOsMuda() throws Exception {
        Campos campos = exigirCampos();
        Payment cobranca = hidratar(campos);

        Resultado r = escritoresPorChamada(Payment.class, cobranca, campos);

        
        
        
        
        
        
        
        
        
        
        
        
        assertThat(r.invocados())
                .as("guarda de vacuidade: o analisador EXERCITOU os métodos de instância que "
                        + "existem (hasCharge fica; attachCharge sai pela pendência 11)")
                .contains("hasCharge");
        assertThat(r.escritores())
                .as("F2 — estes métodos MUDARAM amount ou order_nsu de uma cobrança já criada. "
                        + "O corpo da requisição é congelado (ADR 0016 §3.11): amount diferente na "
                        + "mesma tentativa é segundo link pagável (PV2 §5, B1)")
                .isEmpty();
    }

    @Test
    void f2_paymentNaoExpoeEscritorDeAmountOuOrderNsu() {
        exigirCampos();

        assertThat(escritoresPorAssinatura(Payment.class))
                .as("F2 — Payment expõe caminho de escrita de amount/order_nsu depois de criado "
                        + "(set*, parâmetro BigDecimal, ou nome de valor que não é leitor)")
                .isEmpty();
        assertThat(updatesQueAtribuem(PaymentRepository.class))
                .as("F2 — uma @Query de PaymentRepository faz UPDATE que atribui amount ou "
                        + "order_nsu: contorna a entidade inteira. K2/K3/K5 escrevem status, "
                        + "artefato e rastro — nunca o valor")
                .isEmpty();
    }

    


    @Test
    void f2_controle_osAnalisadoresAcusamMutadoresPlantados() throws Exception {
        Campos campos = new Campos(CobrancaPlantada.class.getDeclaredField("amount"),
                CobrancaPlantada.class.getDeclaredField("orderNsu"), null);
        CobrancaPlantada plantada = new CobrancaPlantada();
        campos.amount().setAccessible(true);
        campos.nsu().setAccessible(true);
        campos.amount().set(plantada, AMOUNT_ORIGINAL);
        campos.nsu().set(plantada, NSU_ORIGINAL);

        assertThat(escritoresPorAssinatura(CobrancaPlantada.class))
                .as("controle da assinatura: reajustar(BigDecimal) TEM de ser acusado")
                .anySatisfy(v -> assertThat(v).contains("reajustar"));
        assertThat(escritoresPorChamada(CobrancaPlantada.class, plantada, campos).escritores())
                .as("controle da chamada: 'sincronizar()' — nome inocente, sem parâmetro — que "
                        + "reescreve order_nsu TEM de ser acusado pela leitura do campo")
                .anySatisfy(v -> assertThat(v).contains("sincronizar"))
                .anySatisfy(v -> assertThat(v).contains("reajustar"));
        assertThat(updatesQueAtribuem(RepositorioPlantado.class))
                .as("controle do repositório: um UPDATE que atribui amount TEM de ser acusado")
                .anySatisfy(v -> assertThat(v).contains("reprecificar"));
    }

    

    static class CobrancaPlantada {
        private BigDecimal amount;
        private String orderNsu;

        public void reajustar(BigDecimal novo) { this.amount = novo; }

        public void sincronizar() { this.orderNsu = this.orderNsu + "-2"; }

        public BigDecimal getAmount() { return amount; }
    }

    interface RepositorioPlantado {
        @Modifying
        @Query("update Payment p set p.amount = :novo where p.id = :id")
        int reprecificar(UUID id, BigDecimal novo);

        @Modifying
        @Query("update Payment p set p.status = 'NOT_CREATED' where p.id = :id and p.status = 'PENDING_CONFIRMATION'")
        int soltar(UUID id);
    }

    

    private record Campos(Field amount, Field nsu, Field status) {}

    private record Resultado(List<String> invocados, List<String> escritores) {}

    private static Campos exigirCampos() {
        Field amount = EstadoDaCobrancaNoBancoTest.campoQueMapeiaAColuna("amount");
        Field nsu = EstadoDaCobrancaNoBancoTest.campoQueMapeiaAColuna("order_nsu");
        assertThat(amount)
                .as("F2, CUIDADO DE VACUIDADE — Payment ainda NÃO TEM o campo que mapeia a coluna "
                        + "amount (ADR 0016 §3.5, §3.7). Um analisador de mutadores sobre classe "
                        + "sem o campo seria verde por ausência. Vermelho de produto da DV0-c")
                .isNotNull();
        assertThat(nsu)
                .as("F2, CUIDADO DE VACUIDADE — Payment ainda NÃO TEM o campo que mapeia a coluna "
                        + "order_nsu")
                .isNotNull();
        assertThat(amount.getType()).as("amount é dinheiro").isEqualTo(BigDecimal.class);
        return new Campos(amount, nsu, EstadoDaCobrancaNoBancoTest.campoQueMapeiaAColuna("status"));
    }

    


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Payment hidratar(Campos campos) throws Exception {
        Constructor<Payment> construtor = Payment.class.getDeclaredConstructor();
        construtor.setAccessible(true);
        Payment p = construtor.newInstance();
        campos.amount().setAccessible(true);
        campos.amount().set(p, AMOUNT_ORIGINAL);
        campos.nsu().setAccessible(true);
        campos.nsu().set(p, NSU_ORIGINAL);
        if (campos.status() != null && campos.status().getType().isEnum()) {
            campos.status().setAccessible(true);
            campos.status().set(p, Enum.valueOf((Class) campos.status().getType(),
                    "PENDING_CONFIRMATION"));
        }
        return p;
    }

    private static List<String> escritoresPorAssinatura(Class<?> tipo) {
        List<String> violacoes = new ArrayList<>();
        for (Method m : tipo.getDeclaredMethods()) {
            if (Modifier.isPrivate(m.getModifiers()) || m.isSynthetic() || m.isBridge()
                    || m.getName().startsWith("$$_hibernate")) continue;
            boolean deInstancia = !Modifier.isStatic(m.getModifiers());
            String nome = m.getName();
            String n = nome.toLowerCase(Locale.ROOT);
            if (nome.startsWith("set")) {
                violacoes.add(assinatura(m) + " — setter");
            } else if (deInstancia && List.of(m.getParameterTypes()).contains(BigDecimal.class)) {
                violacoes.add(assinatura(m) + " — recebe dinheiro depois de criada");
            } else if (deInstancia && PALAVRAS.stream().anyMatch(n::contains)
                    && (m.getParameterCount() > 0 || m.getReturnType() == void.class)) {
                violacoes.add(assinatura(m) + " — nome de valor e não é leitor");
            }
        }
        return violacoes;
    }

    private static Resultado escritoresPorChamada(Class<?> tipo, Object alvo, Campos campos)
            throws Exception {
        List<String> invocados = new ArrayList<>();
        List<String> escritores = new ArrayList<>();
        campos.amount().setAccessible(true);
        campos.nsu().setAccessible(true);
        for (Method m : tipo.getDeclaredMethods()) {
            int mod = m.getModifiers();
            if (Modifier.isPrivate(mod) || Modifier.isStatic(mod) || m.isSynthetic() || m.isBridge()
                    || m.getName().startsWith("$$_hibernate")
                    || List.of("equals", "hashCode", "toString").contains(m.getName())) continue;
            Object amountAntes = campos.amount().get(alvo);
            Object nsuAntes = campos.nsu().get(alvo);
            m.setAccessible(true);
            try {
                m.invoke(alvo, argumentosSinteticos(m));
            } catch (InvocationTargetException | IllegalArgumentException recusou) {
                
            }
            invocados.add(m.getName());
            Object amountDepois = campos.amount().get(alvo);
            Object nsuDepois = campos.nsu().get(alvo);
            if (!mesmoValor(amountAntes, amountDepois)) {
                escritores.add(assinatura(m) + " — amount: " + amountAntes + " → " + amountDepois);
                campos.amount().set(alvo, amountAntes);
            }
            if (!Objects.equals(nsuAntes, nsuDepois)) {
                escritores.add(assinatura(m) + " — order_nsu: " + nsuAntes + " → " + nsuDepois);
                campos.nsu().set(alvo, nsuAntes);
            }
        }
        return new Resultado(invocados, escritores);
    }

    private static final Pattern UPDATE_QUE_ATRIBUI = Pattern.compile(
            "(?is)^\\s*update\\b.*\\bset\\b.*\\b(amount|ordernsu|order_nsu)\\s*=.*");

    private static List<String> updatesQueAtribuem(Class<?> repositorio) {
        List<String> violacoes = new ArrayList<>();
        for (Method m : repositorio.getDeclaredMethods()) {
            Query q = m.getAnnotation(Query.class);
            if (q == null) continue;
            String jpql = q.value();
            String semWhere = jpql.split("(?i)\\bwhere\\b")[0];
            if (UPDATE_QUE_ATRIBUI.matcher(semWhere).matches()) {
                violacoes.add(m.getName() + " — " + jpql);
            }
        }
        return violacoes;
    }

    private static Object[] argumentosSinteticos(Method m) {
        Class<?>[] tipos = m.getParameterTypes();
        Object[] args = new Object[tipos.length];
        for (int i = 0; i < tipos.length; i++) {
            Class<?> t = tipos[i];
            if (t == BigDecimal.class) args[i] = new BigDecimal("12345.67");
            else if (t == Instant.class) args[i] = Instant.parse("2026-10-07T18:00:00Z");
            else if (t == String.class) args[i] = "x";
            else if (t == UUID.class) args[i] = UUID.randomUUID();
            else if (t == boolean.class) args[i] = true;
            else if (t == int.class) args[i] = 7;
            else if (t == long.class) args[i] = 7L;
            else if (t.isEnum() && t.getEnumConstants().length > 0) args[i] = t.getEnumConstants()[0];
            else args[i] = null;
        }
        return args;
    }

    private static boolean mesmoValor(Object a, Object b) {
        if (a == null || b == null) return a == b;
        return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
    }

    private static String assinatura(Method m) {
        StringBuilder s = new StringBuilder(m.getDeclaringClass().getSimpleName())
                .append('.').append(m.getName()).append('(');
        Class<?>[] p = m.getParameterTypes();
        for (int i = 0; i < p.length; i++) {
            if (i > 0) s.append(", ");
            s.append(p[i].getSimpleName());
        }
        return s.append(')').toString();
    }

    


















































}
