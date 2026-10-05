package dev.vinyl.poc.vision;

/** A failed or unusable model call. The message never includes the API key. */
public class VisionException extends RuntimeException {

    public VisionException(String message) {
        super(message);
    }

    public VisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
