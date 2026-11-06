package ma.mobility.air.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Configuration Spring Web Services (contract-first).
 * Le WSDL est généré automatiquement à partir de xsd/air.xsd et publié
 * sur /ws/air.wsdl (le nom du bean "air" donne le nom du fichier WSDL).
 */
@EnableWs
@Configuration
public class WebServiceConfig {

    public static final String NAMESPACE = "http://mobility.ma/air";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext ctx) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(ctx);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "air")
    public DefaultWsdl11Definition airWsdl(XsdSchema airSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("AirPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(NAMESPACE);
        wsdl.setSchema(airSchema);
        return wsdl;
    }

    @Bean
    public XsdSchema airSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/air.xsd"));
    }
}
