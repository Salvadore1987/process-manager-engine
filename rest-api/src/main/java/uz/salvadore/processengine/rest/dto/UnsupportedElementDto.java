package uz.salvadore.processengine.rest.dto;

public record UnsupportedElementDto(
        String element,
        String id,
        String name,
        int line
) {}
