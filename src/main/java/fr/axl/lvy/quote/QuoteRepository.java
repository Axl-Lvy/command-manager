package fr.axl.lvy.quote;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

  @Query("SELECT q FROM Quote q LEFT JOIN FETCH q.client WHERE q.deletedAt IS NULL")
  List<Quote> findByDeletedAtIsNull();
}
