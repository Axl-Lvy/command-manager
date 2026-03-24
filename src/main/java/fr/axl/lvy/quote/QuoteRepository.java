package fr.axl.lvy.quote;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

  List<Quote> findByDeletedAtIsNull();
}
