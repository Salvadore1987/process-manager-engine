package uz.salvadore.processengine.rest.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import uz.salvadore.processengine.core.domain.exception.DuplicateProcessDefinitionException;
import uz.salvadore.processengine.core.parser.BpmnParseException;
import uz.salvadore.processengine.rest.dto.ErrorResponseDto;
import uz.salvadore.processengine.rest.dto.UnsupportedElementDto;
import uz.salvadore.processengine.rest.dto.ValidationResultDto;

import java.time.Instant;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateProcessDefinitionException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicateProcessDefinition(
            DuplicateProcessDefinitionException ex, HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                "CONFLICT", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ProcessNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleProcessNotFound(ProcessNotFoundException ex,
                                                                   HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                "NOT_FOUND", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleDefinitionNotFound(DefinitionNotFoundException ex,
                                                                      HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                "NOT_FOUND", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ValidationResultDto> handleValidationFailed(ValidationFailedException ex) {
        List<UnsupportedElementDto> elements = ex.getValidationResult().unsupportedElements().stream()
                .map(e -> new UnsupportedElementDto(e.element(), e.id(), e.name(), e.line()))
                .toList();
        ValidationResultDto result = new ValidationResultDto(false, elements);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(BpmnParseException.class)
    public ResponseEntity<ErrorResponseDto> handleBpmnParseException(BpmnParseException ex,
                                                                      HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                "BAD_REQUEST", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex,
                                                                   HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                "BAD_REQUEST", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalState(IllegalStateException ex,
                                                                HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                "CONFLICT", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneral(Exception ex, HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                "INTERNAL_SERVER_ERROR", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
