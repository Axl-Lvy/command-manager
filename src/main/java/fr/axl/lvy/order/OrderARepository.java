package fr.axl.lvy.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderARepository extends JpaRepository<OrderA, Long> {

  List<OrderA> findByDeletedAtIsNull();
}
