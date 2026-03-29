package uz.salvadore.processengine.core.port.outgoing;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public interface MessageTransport {

    void send(String topic, UUID correlationId, Map<String, Object> payload);

    void subscribe(String topic, Consumer<MessageResult> callback);

    record MessageResult(UUID correlationId, Map<String, Object> payload, boolean success, String errorCode) {
    }
}
