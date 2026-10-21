package ai.speckitty.charter.packs.infra.api;

import org.apache.catalina.Container;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

/**
 * Makes the servlet container answer as the service does. A request Tomcat rejects before Spring MVC sees it gets
 * the closed {@code Problem} ({@link ProblemErrorReportValve}) instead of an HTML report. {@code TRACE} stays
 * disabled in Tomcat: let through, the dispatcher servlet would echo the request. Its 405 is answered by
 * {@link ProblemErrorController}, which names the methods the service supports.
 */
@Configuration(proxyBeanMethods = false)
public class ConnectorProblems {

    /** For Spring, which finds the configuration by component scan. */
    public ConnectorProblems() {}

    /**
     * The web server customizer. It runs after Spring Boot's own Tomcat customizer, which adds an error report
     * valve of its own, so that the problem valve is the one left on the host.
     *
     * @param mapper the application's JSON mapper, resolved when a report is first written
     * @return the customizer
     */
    @Bean
    WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> problemErrorReport(
            ObjectProvider<ObjectMapper> mapper) {
        return new TomcatProblems(mapper);
    }

    private record TomcatProblems(ObjectProvider<ObjectMapper> mapper)
            implements WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>, Ordered {

        @Override
        public void customize(ConfigurableTomcatWebServerFactory factory) {
            factory.addContextCustomizers(context -> {
                Container host = context.getParent();
                Valve problem = new ProblemErrorReportValve(mapper::getObject);
                for (Valve valve : host.getPipeline().getValves()) {
                    if (valve instanceof ErrorReportValve) {
                        host.getPipeline().removeValve(valve);
                    }
                }
                if (host instanceof StandardHost standard) {
                    standard.setErrorReportValveClass(ProblemErrorReportValve.class.getName());
                }
                host.getPipeline().addValve(problem);
            });
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
