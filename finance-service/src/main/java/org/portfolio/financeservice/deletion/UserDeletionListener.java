package org.portfolio.financeservice.deletion;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class UserDeletionListener {
    private final UserDeletionService deletions;
    private final ObjectMapper mapper;
    private final KafkaTemplate<String, String> kafka;

    @KafkaListener(topics = "user-deletion-requests", groupId = "finance-user-deletion",
            properties = {"value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"})
    public void delete(String json) throws Exception {
        var request = mapper.readValue(json, UserDeletionEvent.class);
        deletions.delete(request.userId());
        var completed = new UserDeletionEvent(request.requestId(), request.userId(), "finance");
        kafka.send("user-deletion-completed", request.userId().toString(), mapper.writeValueAsString(completed))
                .get(10, TimeUnit.SECONDS);
    }
}
