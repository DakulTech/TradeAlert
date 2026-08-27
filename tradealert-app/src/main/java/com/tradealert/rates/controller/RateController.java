package com.tradealert.rates.controller;

import com.tradealert.rates.dto.RateDto;
import com.tradealert.rates.model.Rate;
import com.tradealert.rates.service.RateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rates")
public class RateController {

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<RateDto> ingestRate(@RequestParam String currencyPair,
            @RequestParam double rateValue) {
        return ResponseEntity.ok(RateDto.fromEntity(rateService.ingestRate(currencyPair, rateValue)));
    }

    @GetMapping("/fetch/{currencyPair}")
    public ResponseEntity<RateDto> fetchRate(@PathVariable String currencyPair) {
        Rate rate = rateService.fetchAndIngestRate(currencyPair);
        return rate == null ? ResponseEntity.status(503).build() : ResponseEntity.ok(RateDto.fromEntity(rate));
    }

    @GetMapping
    public ResponseEntity<List<RateDto>> getAllRates() {
        return ResponseEntity.ok(rateService.getAllRates().stream().map(RateDto::fromEntity).toList());
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(rateService.checkApiHealth());
    }
}
