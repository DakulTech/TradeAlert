package com.tradealert.rateservice.producer;

import com.tradealert.rateservice.events.RateEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RateProducer {

    private final KafkaTemplate<String, RateEvent> kafkaTemplate;

    public RateProducer(KafkaTemplate<String, RateEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishRate(RateEvent event) {
        kafkaTemplate.send("rates", event);
    }
}
