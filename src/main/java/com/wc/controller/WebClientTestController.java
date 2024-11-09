package com.wc.controller;

import com.wc.services.WebClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/test")
public class WebClientTestController {
    @Autowired
    WebClientService webClientService;

    @GetMapping("/ping")
    public Mono<String> testWebclient(){
      return webClientService.getPingResponse();
    }
}
