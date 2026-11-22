package ma.mobility.air_bridge.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import ma.mobility.air_bridge.exception.OperatorRefusedException;
import ma.mobility.air_bridge.exception.OperatorUnavailableException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker airPartnerCircuitBreaker(){
        CircuitBreakerConfig config= CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(OperatorUnavailableException.class)
                .ignoreExceptions(OperatorRefusedException.class)
                .build();
        return CircuitBreaker.of("air-partner",config);
    }


}
