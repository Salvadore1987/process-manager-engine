package uz.salvadore.processengine.redis.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    @ConditionalOnMissingBean(name = "processEngineObjectMapper")
    public ObjectMapper processEngineObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    @ConditionalOnMissingBean(ProcessEventStore.class)
    public ProcessEventStore redisEventStore(StringRedisTemplate redisTemplate,
                                             ObjectMapper processEngineObjectMapper) {
        return new RedisEventStore(redisTemplate, processEngineObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDefinitionStore.class)
    public ProcessDefinitionStore redisProcessDefinitionStore(StringRedisTemplate redisTemplate,
                                                               ObjectMapper processEngineObjectMapper) {
        return new RedisProcessDefinitionStore(redisTemplate, processEngineObjectMapper);
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
