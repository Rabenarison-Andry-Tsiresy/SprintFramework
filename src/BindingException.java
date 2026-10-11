package src;

// Exception de binding / validation (Sprint 7 / 7b)
public class BindingException extends RuntimeException {
    public BindingException(String message) {
        super(message);
    }

    public BindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
