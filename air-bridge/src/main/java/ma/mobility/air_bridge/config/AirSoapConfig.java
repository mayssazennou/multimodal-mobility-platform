package ma.mobility.air_bridge.config;

import ma.mobility.air_bridge.client.AirSoapClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import java.time.Duration;

@Configuration
public class AirSoapConfig {
    @Bean
    public Jaxb2Marshaller matshaller(){
        Jaxb2Marshaller m=new Jaxb2Marshaller();
        //Vous indiquez au marshaller où se trouvent les classes générées. Il y lit les annotations JAXB et sait quelle classe correspond à quel élément XML
        m.setContextPath("ma.mobility.airbridge.generated");
        return m;
    }

    @Bean
    public AirSoapClient airSoapClient(Jaxb2Marshaller marshaller, @Value("${air.soap.url}") String url){

        AirSoapClient client=new AirSoapClient();
        client.setDefaultUri(url);  // l'adresse unique du service
        client.setMarshaller(marshaller);  // Java -> XML
        client.setUnmarshaller(marshaller); // XML -> Java

// --- Timeouts : empêchent un partenaire lent ou bloqué de bloquer le bridge ---
// Sans timeout, l'appel SOAP attendrait indéfiniment et aucune exception ne serait levée
// (donc pas de 503, et un futur circuit breaker ne compterait jamais l'échec).
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();

// Temps maximal pour établir la connexion TCP avec le partenaire (service éteint, réseau coupé)
        sender.setConnectionTimeout(Duration.ofSeconds(2));

// Temps maximal d'attente de la réponse une fois la requête envoyée (partenaire figé ou très lent)
// Attention : sur hold/confirm, un dépassement laisse un doute (le partenaire a peut-être traité
// la requête). Ce cas est couvert par l'idempotence (Idempotency-Key + Redis).
        sender.setReadTimeout(Duration.ofSeconds(3));

// Dépassement => WebServiceIOException => OperatorUnavailableException (HTTP 503)
        client.setMessageSender(sender);
        return client;

    }
}
