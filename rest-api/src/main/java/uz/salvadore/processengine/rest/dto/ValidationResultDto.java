package uz.salvadore.processengine.rest.dto;

import java.util.List;

public record ValidationResultDto(
        boolean valid,
        List<UnsupportedElementDto> unsupportedElements
) {}
