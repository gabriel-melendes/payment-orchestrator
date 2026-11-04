package com.example.paymentorchestrator.service;

import com.example.paymentorchestrator.dto.StatusResponse;
import org.springframework.stereotype.Service;

@Service
public class StatusService {

    public StatusResponse getStatus() {
        return new StatusResponse("UP");
    }
}
