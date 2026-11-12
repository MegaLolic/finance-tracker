package org.portfolio.financeservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@Component
public class TransactionEventProducer {
    private final String topic;

    private final KafkaTemplate<String,TransactionCreatedEvent> kafkaTemplate;

    public TransactionEventProducer(KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate, @Value("${topic.name}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendTransactionCreated(TransactionCreatedEvent event){
        try {
            kafkaTemplate.send(topic,event.transactionId().toString(),event)
                .whenComplete((result,ex)->{
                    if (ex != null) {
                        log.error("Failed to send event: {}", event, ex);
                    }else{
                        log.info("Sent event: {}",event);
                    }
                });
        } catch (RuntimeException exception) {
            // The database commit has succeeded; reporting failure to the caller could cause duplicates.
            log.error("Failed to send committed transaction event: {}", event, exception);
        }
    }
}
