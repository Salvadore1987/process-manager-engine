package uz.salvadore.processengine.core.engine.condition;

import java.util.Map;

/**
 * Evaluates condition expressions against process variables.
 */
public interface ConditionEvaluator {

    boolean evaluate(String expression, Map<String, Object> variables);
}
