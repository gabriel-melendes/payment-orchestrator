package com.example.paymentorchestrator.controller;

import com.example.paymentorchestrator.dto.StatusResponse;
import com.example.paymentorchestrator.service.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping
    public StatusResponse getStatus() {
        return statusService.getStatus();
    }
}
