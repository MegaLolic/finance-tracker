package org.portfolio.financeservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@Configuration
public class TransactionEventProducer {
    private static final String TOPIC="transaction-events";

    private final KafkaTemplate<String,TransactionCreatedEvent> kafkaTemplate;

    public TransactionEventProducer(KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendTransactionCreated(TransactionCreatedEvent event){
        kafkaTemplate.send(TOPIC,event.transactionId().toString(),event)
                .whenComplete((result,ex)->{
                    if (ex != null) {
                        log.error("Failed to send event: {}", event);
                    }else{
                        log.info("Sent event: {}",event);
                    }
                });
    }
}
