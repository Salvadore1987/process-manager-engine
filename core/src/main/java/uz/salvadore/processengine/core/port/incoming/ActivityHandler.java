package uz.salvadore.processengine.core.port.incoming;

import java.util.Map;

public interface ActivityHandler {

    String getTaskType();

    Map<String, Object> handle(Map<String, Object> variables);
}
