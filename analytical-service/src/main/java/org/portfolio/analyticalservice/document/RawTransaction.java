package org.portfolio.analyticalservice.document;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "raw_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawTransaction {

    @Id
    private Long id;

    private Long userId;
    private Long accountId;
    private Long categoryId;
    private BigDecimal amount;
    private String description;
    private String categoryType;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
