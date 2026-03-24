package fr.axl.lvy.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceBRepository extends JpaRepository<InvoiceB, Long> {

  List<InvoiceB> findByDeletedAtIsNull();
}
