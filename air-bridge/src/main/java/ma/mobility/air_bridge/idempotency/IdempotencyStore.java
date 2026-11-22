package ma.mobility.air_bridge.idempotency;

import java.util.Optional;

public interface IdempotencyStore {
    /**
     * Pose la clé avec le statut IN_PROGRESS, seulement si elle n'existe pas.
     * @return true si on est le premier, false si la clé existait déjà
     */
    boolean tryLock(String key,String fingerprint);


    /**
     * @return entry  si on est le clé existe, rien si la clé existait déjà
     */
    Optional<Entry> get(String key);


    void saveResult(String key, String fingerprint,String result);

    void release(String key);
}
