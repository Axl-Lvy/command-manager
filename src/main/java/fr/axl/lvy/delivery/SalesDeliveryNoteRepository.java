package fr.axl.lvy.delivery;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesDeliveryNoteRepository extends JpaRepository<SalesDeliveryNote, Long> {

  List<SalesDeliveryNote> findByDeletedAtIsNull();
}
