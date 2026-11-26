package ma.mobility.carshare.domain;

import jakarta.persistence.*;

/** Résultat de la première exécution d une opération portant une Idempotency-Key (clé = opération + ":" + valeur). */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {
    @Id private String recordKey;
    @Column(length = 4000) private String response;

    protected IdempotencyRecord() { }

    public IdempotencyRecord(String recordKey, String response) {
        this.recordKey = recordKey; this.response = response;
    }

    public String getResponse() { return response; }
}
