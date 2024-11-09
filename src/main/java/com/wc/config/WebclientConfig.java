package com.wc.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;


@Configuration
public class WebclientConfig {
    @Bean
    public WebClient webclient() throws SSLException {
        //ssl configuration
        SslContext sslContext = SslContextBuilder
                .forClient()
                //.keyManager()
                //.trustManager()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)// disable SSL check for test code
                .build();
        //Connection pooling configuration
        ConnectionProvider connectionProvider = ConnectionProvider.builder("myConnectionPool")
                .maxConnections(500)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(120)).build();

        //reactive HttpClient configuration
        HttpClient httpClient = HttpClient.create(connectionProvider)
                //enable compression in HttpClient
                .compress(true)
                //enable logging of Http request and response
                .wiretap(true)
                .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofMillis(5000))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS)));
        //add default Circuit breaker configuration
        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.ofDefaults()
                .circuitBreaker("myCircuitBreaker", CircuitBreakerConfig.ofDefaults());


        WebClient client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest()) //log request
                .filter(logResponse()) //log response
                //default header for each request
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                //add retry mechanism
                .filter((request, next) -> next.exchange(request)
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                .doAfterRetry(retrySignal -> System.out.println("Retrying ..."))))
                //add circuit breaker
                .filter((request, next) -> circuitBreaker.executeSupplier(() -> next.exchange(request)))
                .build();

        return client;
    }
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            Logger logger = LoggerFactory.getLogger(WebClient.class);
            logger.info("Webclient Request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            Logger logger = LoggerFactory.getLogger(WebClient.class);
            logger.info("Webclient Response: {}", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }
}
