package uz.salvadore.processengine.worker.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import uz.salvadore.processengine.worker.ExternalTaskHandler;
import uz.salvadore.processengine.worker.annotation.JobWorker;

import java.lang.reflect.Method;

/**
 * Scans every Spring bean for {@link ExternalTaskHandler} implementations
 * with {@link JobWorker}-annotated {@code execute} methods and registers them
 * in the {@link TaskHandlerRegistry}.
 */
public class TaskHandlerBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(TaskHandlerBeanPostProcessor.class);

    private final TaskHandlerRegistry registry;

    public TaskHandlerBeanPostProcessor(TaskHandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof ExternalTaskHandler handler)) {
            return bean;
        }

        for (Method method : bean.getClass().getDeclaredMethods()) {
            JobWorker annotation = method.getAnnotation(JobWorker.class);
            if (annotation != null) {
                String topic = annotation.topic();
                log.info("Registering @JobWorker for topic '{}': {}", topic, bean.getClass().getName());
                registry.register(topic, handler);
            }
        }

        return bean;
    }
}
