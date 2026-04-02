package uz.salvadore.processengine.redis.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import uz.salvadore.processengine.core.port.outgoing.InstanceDefinitionMapping;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.redis.RedisEventStore;
import uz.salvadore.processengine.redis.RedisInstanceDefinitionMapping;
import uz.salvadore.processengine.redis.RedisProcessDefinitionStore;
import uz.salvadore.processengine.redis.RedisSequenceGenerator;

@AutoConfiguration
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
}
