package fr.axl.lvy.delivery;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryNoteARepository extends JpaRepository<DeliveryNoteA, Long> {

  List<DeliveryNoteA> findByDeletedAtIsNull();
}
