package com.tradealert.rateservice.controller;

import com.tradealert.rateservice.dto.RateDTO;
import com.tradealert.rateservice.model.Rate;
import com.tradealert.rateservice.service.RateService;
import com.tradealert.rateservice.route.RateRoutes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(RateRoutes.BASE)
public class RateController {

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @PostMapping(RateRoutes.INGEST)
    public ResponseEntity<RateDTO> ingestRate(@RequestParam String currencyPair,
            @RequestParam double rateValue) {
        Rate rate = rateService.ingestRate(currencyPair, rateValue);
        return ResponseEntity.ok(RateDTO.fromEntity(rate));
    }

    @GetMapping(RateRoutes.FETCH + "/{currencyPair}")
    public ResponseEntity<RateDTO> fetchRate(@PathVariable String currencyPair) {
        Rate rate = rateService.fetchAndIngestRate(currencyPair);
        if (rate != null) {
            return ResponseEntity.ok(RateDTO.fromEntity(rate));
        } else {
            return ResponseEntity.status(503).build();
        }
    }

    @GetMapping(RateRoutes.GET_ALL)
    public ResponseEntity<List<RateDTO>> getAllRates() {
        List<RateDTO> rates = rateService.getAllRates()
                .stream()
                .map(RateDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(rates);
    }

    @GetMapping(RateRoutes.HEALTH)
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(rateService.checkApiHealth());
    }
}
