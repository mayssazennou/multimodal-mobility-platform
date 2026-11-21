package ma.mobility.air_bridge.idempotency;

import lombok.AllArgsConstructor;
import ma.mobility.air_bridge.exception.OperatorUnavailableException;
import ma.mobility.air_bridge.model.Booking;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;

import java.time.Duration;
import java.util.Optional;

@AllArgsConstructor
@Component
public class RedisIdempotencyStore implements IdempotencyStore{
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    @Override
    public boolean tryLock(String key,String fingerprint){
        try {
            Entry entry=new Entry(fingerprint,Status.IN_PROGRESS,null);
            String json=mapper.writeValueAsString(entry);
            Boolean created=redis.opsForValue().setIfAbsent(key,json,LOCK_TTL);
            return Boolean.TRUE.equals(created);
        }
        catch (DataAccessException | JacksonException e){
            throw new OperatorUnavailableException("Magasin d'idempotence indisponible", e);
        }
    }

    @Override
    public Optional<Entry> get(String key){
        try {
            String json=redis.opsForValue().get(key);
            if (json==null){
                return Optional.empty();
            }
            Entry entry=mapper.readValue(json,Entry.class);
            return Optional.of(entry);
        }
        catch (JacksonException | DataAccessException e){
            throw new OperatorUnavailableException("Magasin d'idempotence indisponible",e);
        }
    }

    @Override
    public void saveResult(String key,String fingerprint,String result){
        try {
            Entry entry=new Entry(fingerprint,Status.DONE,result);
            String json=mapper.writeValueAsString(entry);
            redis.opsForValue().set(key,json,RESULT_TTL);
        }
        catch (DataAccessException | JacksonException e){
            throw new OperatorUnavailableException("Magasin d'idempotence indisponible",e);
        }
    }

    @Override
    public void release(String key){
        try {
            redis.delete(key);
        }
        catch (DataAccessException e){
            throw new OperatorUnavailableException("Magasin d'idempotence indisponible",e);
        }
    }
}
