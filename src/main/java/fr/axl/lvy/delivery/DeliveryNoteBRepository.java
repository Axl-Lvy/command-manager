package fr.axl.lvy.delivery;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryNoteBRepository extends JpaRepository<DeliveryNoteB, Long> {

  List<DeliveryNoteB> findByDeletedAtIsNull();
}
