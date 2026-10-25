package com.schwab.shortener.infrastructure;

import com.schwab.shortener.domain.ClickEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> { List<ClickEvent> findByLinkId(Long linkId); }
