package ma.mobility.air_bridge;

import ma.mobility.air_bridge.client.AirSoapClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

import javax.xml.transform.Source;

// 1. Les 3 imports statiques indispensables ajoutés ici :
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

@SpringBootTest
public class AirSoapClientTest {

    // 2. Déclaration de la constante NAMESPACE (à adapter selon votre fichier .xsd)
    private static final String NAMESPACE = "http://mobility.ma";

    @Autowired
    private AirSoapClient airSoapClient;

    private MockWebServiceServer mockServer;

    @BeforeEach
    void init() {
        mockServer = MockWebServiceServer.createServer(airSoapClient);
    }

    @Test
    void testHoldFlight() {
        Source expectedRequest = new StringSource(
                "<ns:HoldFlightRequest xmlns:ns='" + NAMESPACE + "'>" +
                        "<ns:flightId>AT202</ns:flightId>" +
                        "<ns:seats>2</ns:seats>" +
                        "</ns:HoldFlightRequest>"
        );
        Source mockResponse = new StringSource(
                "<ns:HoldFlightResponse xmlns:ns='" + NAMESPACE + "'>" +
                        "<ns:holdId>HOLD-9988</ns:holdId>" +
                        "</ns:HoldFlightResponse>"
        );

        mockServer.expect(payload(expectedRequest)).andRespond(withPayload(mockResponse));

        // 3. Correction de "AT2T2" -> "AT202" pour correspondre à expectedRequest
        String holdId = airSoapClient.holdFlight("AT202", 2);

        assertEquals("HOLD-9988", holdId);
        mockServer.verify();
    }
}
