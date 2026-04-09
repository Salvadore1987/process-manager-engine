package uz.salvadore.processengine.redis.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import uz.salvadore.processengine.core.port.outgoing.ActivityLog;
import uz.salvadore.processengine.core.port.outgoing.BusinessKeyMapping;
import uz.salvadore.processengine.core.port.outgoing.ChildInstanceMapping;
import uz.salvadore.processengine.core.port.outgoing.InstanceDefinitionMapping;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessInstanceLock;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.redis.RedisBusinessKeyMapping;
import uz.salvadore.processengine.redis.RedisChildInstanceMapping;
import uz.salvadore.processengine.redis.RedisEventStore;
import uz.salvadore.processengine.redis.RedisActivityLog;
import uz.salvadore.processengine.redis.RedisInstanceDefinitionMapping;
import uz.salvadore.processengine.redis.RedisProcessDefinitionStore;
import uz.salvadore.processengine.redis.RedisProcessInstanceLock;
import uz.salvadore.processengine.redis.RedisSequenceGenerator;

@AutoConfiguration(beforeName = {
        "uz.salvadore.processengine.spring.autoconfigure.EventStoreAutoConfiguration",
        "uz.salvadore.processengine.spring.autoconfigure.ProcessEngineAutoConfiguration"
})
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProcessEventStore.class)
    public ProcessEventStore redisEventStore(StringRedisTemplate redisTemplate,
                                             ObjectMapper objectMapper) {
        return new RedisEventStore(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDefinitionStore.class)
    public ProcessDefinitionStore redisProcessDefinitionStore(StringRedisTemplate redisTemplate,
                                                               ObjectMapper objectMapper) {
        return new RedisProcessDefinitionStore(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(SequenceGenerator.class)
    public SequenceGenerator redisSequenceGenerator(StringRedisTemplate redisTemplate) {
        return new RedisSequenceGenerator(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(InstanceDefinitionMapping.class)
    public InstanceDefinitionMapping redisInstanceDefinitionMapping(StringRedisTemplate redisTemplate) {
        return new RedisInstanceDefinitionMapping(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessInstanceLock.class)
    public ProcessInstanceLock redisProcessInstanceLock(StringRedisTemplate redisTemplate) {
        return new RedisProcessInstanceLock(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(BusinessKeyMapping.class)
    public BusinessKeyMapping redisBusinessKeyMapping(StringRedisTemplate redisTemplate) {
        return new RedisBusinessKeyMapping(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ChildInstanceMapping.class)
    public ChildInstanceMapping redisChildInstanceMapping(StringRedisTemplate redisTemplate) {
        return new RedisChildInstanceMapping(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ActivityLog.class)
    public ActivityLog redisActivityLog(StringRedisTemplate redisTemplate,
                                        ObjectMapper objectMapper) {
        return new RedisActivityLog(redisTemplate, objectMapper);
    }
}
