package com.djorka.incidentops.controller;

import com.djorka.incidentops.dto.EmailOutboxProcessResponse;
import com.djorka.incidentops.service.EmailOutboxProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/email-outbox")
@RequiredArgsConstructor
public class EmailOutboxController {

    private final EmailOutboxProcessor processor;

    @GetMapping("/process")
    public ResponseEntity<EmailOutboxProcessResponse> process() {
        return ResponseEntity.ok(processor.processBatch());
    }
}
