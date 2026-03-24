package fr.axl.lvy.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {

  List<PurchaseInvoice> findByDeletedAtIsNull();
}
