package fr.axl.lvy.delivery;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseDeliveryNoteRepository extends JpaRepository<PurchaseDeliveryNote, Long> {

  List<PurchaseDeliveryNote> findByDeletedAtIsNull();
}
