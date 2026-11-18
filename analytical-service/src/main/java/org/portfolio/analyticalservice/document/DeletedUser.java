package org.portfolio.analyticalservice.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "deleted_users")
public class DeletedUser {
    @Id
    private Long id;
}
