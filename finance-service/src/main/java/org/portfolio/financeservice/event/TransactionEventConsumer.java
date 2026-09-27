package org.portfolio.financeservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionEventConsumer {

    @KafkaListener(
            topics = "transaction-events",
            groupId = "finance-service"
    )
    public void consume(TransactionCreatedEvent event) {

        log.info(
                "Received transaction event: {}",
                event
        );
    }
}