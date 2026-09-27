package org.portfolio.financeservice.event;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class TransactionEventProducer {
    private static final String TOPIC="transaction-events";

    private final KafkaTemplate<String,TransactionCreatedEvent> kafkaTemplate;

    public TransactionEventProducer(KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendTransactionCreated(TransactionCreatedEvent event){
        kafkaTemplate.send(TOPIC,event.transactionId().toString(),event);
    }
}
