package Backend.BrechoAPI;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
























































































final class BrCodeChecker {

    private BrCodeChecker() {
    }

    
    private static final String CHECKSUM_TAG = "63";
    private static final int CHECKSUM_LENGTH = 4;

    
    static final String MERCHANT_ACCOUNT_TAG = "26";

    
    static final String URL_SUBTAG = "25";

    
    static final String PIX_KEY_SUBTAG = "01";

    
    static final String ADDITIONAL_DATA_TAG = "62";
    static final String REFERENCE_LABEL_TAG = "05";

    
    enum Domain {
        BR_CODE, OUT_OF_DOMAIN
    }

    




    enum FormRegime {
        STATIC_QR, DYNAMIC_QR, UNDETERMINED
    }

    
    record Tag(String id, String value) {
    }

    



    record Inspection(Domain domain,
                      String outOfDomainReason,
                      List<Tag> tags,
                      String declaredChecksum,
                      String computedChecksum) {

        boolean inDomain() {
            return domain == Domain.BR_CODE;
        }

        
        boolean checksumMatches() {
            return inDomain() && declaredChecksum.equals(computedChecksum);
        }

        
        String checksumComplaint() {
            if (!inDomain()) {
                return outOfDomainReason;
            }
            return "CRC esperado " + computedChecksum
                    + " (calculado sobre o payload sem os 4 últimos caracteres)"
                    + " × encontrado " + declaredChecksum
                    + " (declarado no campo " + CHECKSUM_TAG + " do próprio payload)";
        }

        
        String tag(String id) {
            return tags.stream()
                    .filter(t -> t.id().equals(id))
                    .map(Tag::value)
                    .findFirst()
                    .orElse(null);
        }

        
        List<String> tagIds() {
            return tags.stream().map(Tag::id).toList();
        }

        



        String subTag(String parentId, String childId) {
            String parent = tag(parentId);
            if (parent == null) {
                return null;
            }
            Inspection nested = parse(parent, false);
            return nested.inDomain() ? nested.tag(childId) : null;
        }

        




        FormRegime formRegime() {
            if (subTag(MERCHANT_ACCOUNT_TAG, URL_SUBTAG) != null) {
                return FormRegime.DYNAMIC_QR;
            }
            if (subTag(MERCHANT_ACCOUNT_TAG, PIX_KEY_SUBTAG) != null) {
                return FormRegime.STATIC_QR;
            }
            return FormRegime.UNDETERMINED;
        }

        
        String referenceLabel() {
            return subTag(ADDITIONAL_DATA_TAG, REFERENCE_LABEL_TAG);
        }
    }

    







    static Inspection inspect(String payload) {
        return parse(payload, true);
    }

    private static Inspection parse(String payload, boolean requireChecksumTag) {
        if (payload == null) {
            return outOfDomain("payload nulo — não há forma a conferir");
        }
        if (payload.isBlank()) {
            return outOfDomain("payload em branco — não há forma a conferir");
        }

        List<Tag> tags = new ArrayList<>();
        int i = 0;
        while (i < payload.length()) {
            if (i + 4 > payload.length()) {
                return outOfDomain("parse TLV interrompido na posição " + i + ": sobram "
                        + (payload.length() - i) + " caractere(s) ("
                        + recorte(payload, i) + "), menos que o cabeçalho de 4"
                        + " (identificador de 2 + tamanho de 2)");
            }
            String id = payload.substring(i, i + 2);
            String declaredLength = payload.substring(i + 2, i + 4);
            if (!isTwoDigits(id)) {
                return outOfDomain("parse TLV interrompido na posição " + i
                        + ": identificador \"" + id + "\" não é numérico de dois dígitos"
                        + " (trecho: " + recorte(payload, i) + ")");
            }
            if (!isTwoDigits(declaredLength)) {
                return outOfDomain("parse TLV interrompido na posição " + (i + 2)
                        + ": tamanho \"" + declaredLength + "\" do campo \"" + id
                        + "\" não é numérico de dois dígitos (trecho: " + recorte(payload, i) + ")");
            }
            int length = Integer.parseInt(declaredLength);
            if (i + 4 + length > payload.length()) {
                return outOfDomain("parse TLV interrompido na posição " + (i + 4)
                        + ": o campo \"" + id + "\" declara " + length
                        + " caractere(s) e só há " + (payload.length() - i - 4)
                        + " até o fim do payload");
            }
            tags.add(new Tag(id, payload.substring(i + 4, i + 4 + length)));
            i += 4 + length;
        }

        if (tags.isEmpty()) {
            return outOfDomain("parse TLV não produziu campo nenhum");
        }

        Tag last = tags.get(tags.size() - 1);
        if (requireChecksumTag
                && !(last.id().equals(CHECKSUM_TAG) && last.value().length() == CHECKSUM_LENGTH)) {
            return outOfDomain("parse TLV completo, mas o payload não termina no campo \""
                    + CHECKSUM_TAG + "\" de " + CHECKSUM_LENGTH + " caracteres — último campo:"
                    + " \"" + last.id() + "\" com " + last.value().length()
                    + " caractere(s). Campos lidos: " + tags.stream().map(Tag::id).toList());
        }

        String declared = requireChecksumTag ? last.value() : null;
        String computed = requireChecksumTag
                ? checksumOf(payload.substring(0, payload.length() - CHECKSUM_LENGTH))
                : null;

        return new Inspection(Domain.BR_CODE, null, List.copyOf(tags), declared, computed);
    }

    private static Inspection outOfDomain(String reason) {
        return new Inspection(Domain.OUT_OF_DOMAIN,
                "fora de domínio (não é BR Code): " + reason,
                List.of(), null, null);
    }

    private static String recorte(String payload, int from) {
        int to = Math.min(payload.length(), from + 12);
        return "\"" + payload.substring(from, to) + (to < payload.length() ? "…" : "") + "\"";
    }

    private static boolean isTwoDigits(String value) {
        return value.length() == 2
                && Character.isDigit(value.charAt(0))
                && Character.isDigit(value.charAt(1));
    }

    

    private static final int[] TABLE = buildTable();

    private static int[] buildTable() {
        int[] table = new int[256];
        for (int b = 0; b < 256; b++) {
            int value = b << 8;
            for (int bit = 0; bit < 8; bit++) {
                value = (value & 0x8000) != 0 ? (value << 1) ^ 0x1021 : value << 1;
                value &= 0xFFFF;
            }
            table[b] = value;
        }
        return table;
    }

    







    static String checksumOf(String payload) {
        int acc = 0xFFFF;
        for (byte b : payload.getBytes(StandardCharsets.UTF_8)) {
            acc = ((acc << 8) ^ TABLE[((acc >> 8) ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        }
        return String.format("%04X", acc);
    }
}
