package hms;

/** One simple error type. The message is always safe to show to the admin. */
public class HmsException extends RuntimeException {
    public HmsException(String message) { super(message); }
    public HmsException(String message, Throwable cause) { super(message, cause); }
}
