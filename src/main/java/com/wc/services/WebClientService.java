package com.wc.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WebClientService {
    @Autowired
    WebClient webClient;
    public Mono<String> getPingResponse(){
        Mono<String> response =webClient.get()
                .uri("http://www.abc.com/")
                .retrieve()
                .onStatus(
                        HttpStatus.INTERNAL_SERVER_ERROR::equals,
                        error -> error.bodyToMono(String.class).map(Exception::new))
                .onStatus(
                        HttpStatus.BAD_REQUEST::equals,
                        error -> error.bodyToMono(String.class).map(Exception::new))
                .bodyToMono(String.class);
        return response;
    }
}
