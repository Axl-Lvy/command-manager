package fr.axl.lvy.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceARepository extends JpaRepository<InvoiceA, Long> {

  List<InvoiceA> findByDeletedAtIsNull();
}
