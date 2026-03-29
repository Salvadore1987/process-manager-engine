package uz.salvadore.processengine.core.parser;

public record UnsupportedElementError(
        String element,
        String id,
        String name,
        int line
) {
}
