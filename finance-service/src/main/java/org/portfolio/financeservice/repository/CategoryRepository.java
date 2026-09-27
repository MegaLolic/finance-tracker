package org.portfolio.financeservice.repository;

import org.portfolio.financeservice.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category,Long> {
    List<Category> findCategoriesByUserId(Long userId);
}
