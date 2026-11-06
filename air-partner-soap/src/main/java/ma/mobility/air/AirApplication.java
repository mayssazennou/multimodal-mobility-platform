package ma.mobility.air;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Partenaire aérien — service SOAP (Spring Boot + Spring Web Services).
 * FOURNI dans le starter kit. Ne pas modifier.
 *
 * WSDL exposé à l'exécution : http://localhost:8081/ws/air.wsdl
 * (port conteneur 8080, publié sur 8081 — voir docker-compose.yml)
 *
 * Encadrement : Pr. Mahmoud NASSAR
 */
@SpringBootApplication
public class AirApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirApplication.class, args);
    }
}
