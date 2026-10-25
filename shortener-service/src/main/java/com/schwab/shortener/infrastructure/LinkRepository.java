package com.schwab.shortener.infrastructure;

import com.schwab.shortener.domain.Link;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkRepository extends JpaRepository<Link, Long> {
  Optional<Link> findByCode(String code);
  Optional<Link> findByIdempotencyKey(String idempotencyKey);
  boolean existsByCode(String code);
}
