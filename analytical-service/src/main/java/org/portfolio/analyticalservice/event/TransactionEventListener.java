package org.portfolio.analyticalservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.portfolio.analyticalservice.document.RawTransaction;
import org.portfolio.analyticalservice.repository.RawTransactionRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {
    private final RawTransactionRepository repository;
    private static final String orderTopic="${topic.name}";


    @KafkaListener(topics = orderTopic, groupId = "analytical-service")
    public void onTransactionCreated(TransactionCreatedEvent event){
        log.info("Received event: {}",event);

        RawTransaction doc=RawTransaction.builder()
                .id(event.transactionId())
                .userId(event.userId())
                .accountId(event.accountId())
                .categoryId(event.categoryId())
                .description(event.description())
                .amount(event.amount())
                .createdAt(event.createdAt())
                .build();

        repository.save(doc);
        log.info("Saved to MongoDB: {}",doc.getId());
    }
}
