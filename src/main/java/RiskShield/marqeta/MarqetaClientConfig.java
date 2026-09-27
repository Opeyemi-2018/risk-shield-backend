package RiskShield.marqeta;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class MarqetaClientConfig {

    @Bean
    public RestClient marqetaRestClient(MarqetaProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(
                        properties.applicationToken(),
                        properties.adminAccessToken()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}