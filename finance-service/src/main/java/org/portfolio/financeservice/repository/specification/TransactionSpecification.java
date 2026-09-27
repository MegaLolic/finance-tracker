package org.portfolio.financeservice.repository.specification;


import jakarta.persistence.criteria.Predicate;
import org.portfolio.financeservice.dto.TransactionFilterDto;
import org.portfolio.financeservice.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class TransactionSpecification {

    public static Specification<Transaction> filter(TransactionFilterDto filter) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), filter.getUserId()));
            }
            if (filter.getAccountId() != null) {
                predicates.add(cb.equal(root.get("accountId"), filter.getAccountId()));
            }
            if (filter.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filter.getCategoryId()));
            }
            if (filter.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFrom()));
            }
            if (filter.getTo() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getTo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
