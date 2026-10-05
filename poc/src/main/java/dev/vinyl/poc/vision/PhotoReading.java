package dev.vinyl.poc.vision;

/** The role the model gave one photo. The id is photo_1, photo_2, ... in the order the photos were sent. */
public record PhotoReading(String id, PhotoRole role) {
}
