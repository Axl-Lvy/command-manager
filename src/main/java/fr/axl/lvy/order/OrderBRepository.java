package fr.axl.lvy.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderBRepository extends JpaRepository<OrderB, Long> {

  List<OrderB> findByDeletedAtIsNull();

  List<OrderB> findByOrderAId(Long orderAId);
}
