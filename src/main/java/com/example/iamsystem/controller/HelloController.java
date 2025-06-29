package com.example.iamsystem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HelloController {

    @GetMapping("/hello")
    @Operation(summary = "A simple hello world endpoint")
    @SecurityRequirement(name = "")
    public String hello() {
        log.info("Hello endpoint called.");
        return "Hello World";
    }

}
