package ma.mobility.rail.api;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Rejeu sûr: une même Idempotency-Key renvoie le résultat de la première exécution sans rappeler l'opérateur.
 * Limite: en mémoire (perdu au redémarrage)
 */
@Component
public class IdempotencyStore {
    private final ConcurrentMap<String, Object> done = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T once(String operation, String key, Supplier<T> action) {
        if (key == null || key.trim().isEmpty()) {
            return action.get();
        }
        return (T) done.computeIfAbsent(operation + ":" + key, k -> action.get());
    }
}