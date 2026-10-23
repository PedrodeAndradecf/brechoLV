package Backend.BrechoAPI.service.impl;

import Backend.BrechoAPI.service.PaymentGateway;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;




















































@Service
public class FakePixPaymentGateway implements PaymentGateway {

    




    private static final String MARCA_DE_FALSO = "FAKE";

    












    private static final int DIGITOS_HEX_DO_TXID = 21;

    
    private static final String CHAVE_PIX_FALSA = "fake@brecho.local";

    private static final String NOME_DO_RECEBEDOR = "BRECHO FAKE";
    private static final String CIDADE_DO_RECEBEDOR = "SAO PAULO";

    @Override
    public PixCharge createPixCharge(UUID orderId, BigDecimal amount, Instant expiresAt) {
        String txid = derivarTxid(orderId);
        return new PixCharge(txid, montarBrCode(txid));
    }

    



    private static String derivarTxid(UUID orderId) {
        byte[] digest = sha256(orderId.toString());
        String hex = HexFormat.of().withUpperCase().formatHex(digest);
        return MARCA_DE_FALSO + hex.substring(0, DIGITOS_HEX_DO_TXID);
    }

    private static byte[] sha256(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            
            
            
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

    














    private static String montarBrCode(String txid) {
        String contaDoRecebedor =
                campo("00", "br.gov.bcb.pix") + campo("01", CHAVE_PIX_FALSA);

        String semCrc = campo("00", "01")               
                + campo("01", "12")                      
                + campo("26", contaDoRecebedor)          
                + campo("52", "0000")                    
                + campo("53", "986")                     
                + campo("58", "BR")                      
                + campo("59", NOME_DO_RECEBEDOR)
                + campo("60", CIDADE_DO_RECEBEDOR)
                + campo("62", campo("05", txid))         
                + "6304";                                

        return semCrc + crc16(semCrc);
    }

    
    private static String campo(String id, String valor) {
        return id + String.format("%02d", valor.length()) + valor;
    }

    
    private static String crc16(String payload) {
        int crc = 0xFFFF;
        for (byte b : payload.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
