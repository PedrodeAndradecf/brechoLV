package Backend.BrechoAPI.exception;















































public class PersistenceIntegrityException extends RuntimeException {

    

    public static final String MENSAGEM =
            "não foi possível registrar o pedido; tente de novo em instantes";

    



    public static final String MENSAGEM_LEITURA_INCONSISTENTE =
            "não foi possível carregar o recurso; tente de novo em instantes";

    public PersistenceIntegrityException() {
        super(MENSAGEM);
    }

    

    private PersistenceIntegrityException(String mensagemVetada) {
        super(mensagemVetada);
    }

    


    public static PersistenceIntegrityException leituraInconsistente() {
        return new PersistenceIntegrityException(MENSAGEM_LEITURA_INCONSISTENTE);
    }
}
