package dev.vinyl.poc.discogs;

/** A failed Discogs call. The message never includes credentials. */
public class DiscogsException extends RuntimeException {

    public DiscogsException(String message) {
        super(message);
    }

    public DiscogsException(String message, Throwable cause) {
        super(message, cause);
    }
}
