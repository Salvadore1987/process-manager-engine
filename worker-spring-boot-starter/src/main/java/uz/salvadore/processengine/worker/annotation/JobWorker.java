package uz.salvadore.processengine.worker.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@code execute} method of an {@link uz.salvadore.processengine.worker.ExternalTaskHandler}
 * as a worker for a specific process engine task topic.
 *
 * <pre>{@code
 * @Component
 * public class OrderValidationHandler implements ExternalTaskHandler {
 *
 *     @Override
 *     @JobWorker(topic = "order.validate")
 *     public void execute(TaskContext context) {
 *         try {
 *             String orderId = (String) context.getVariable("orderId");
 *             context.complete(Map.of("validationResult", "OK"));
 *         } catch (Exception e) {
 *             context.error("VALIDATION_FAILED", e.getMessage());
 *         }
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JobWorker {

    /**
     * The topic name this worker subscribes to.
     */
    String topic();

    /**
     * Whether to enable automatic retry on handler exceptions.
     * Disabled by default — retries must be explicitly opted into.
     */
    boolean retry() default false;

    /**
     * Maximum number of retry attempts before giving up.
     * Only applies when {@link #retry()} is {@code true}.
     */
    int retryCount() default 3;

    /**
     * Backoff interval between retry attempts in milliseconds.
     * Only applies when {@link #retry()} is {@code true}.
     */
    long retryBackoff() default 1000;
}
