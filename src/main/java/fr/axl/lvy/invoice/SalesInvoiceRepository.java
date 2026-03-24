package fr.axl.lvy.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {

  List<SalesInvoice> findByDeletedAtIsNull();
}
