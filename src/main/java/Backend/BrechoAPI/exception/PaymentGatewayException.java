package Backend.BrechoAPI.exception;













































public class PaymentGatewayException extends RuntimeException {

    


    public static final String MENSAGEM_DO_DESCONHECIDO =
            "não foi possível confirmar se a cobrança Pix foi gerada; seu pedido está registrado"
                    + " e em análise, e não é preciso fechar outro";

    private final boolean outcomeUnknown;

    

    public PaymentGatewayException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private PaymentGatewayException(String message, Throwable cause, boolean outcomeUnknown) {
        super(message, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    


    public static PaymentGatewayException outcomeUnknown(Throwable cause) {
        return new PaymentGatewayException(MENSAGEM_DO_DESCONHECIDO, cause, true);
    }

    
    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
