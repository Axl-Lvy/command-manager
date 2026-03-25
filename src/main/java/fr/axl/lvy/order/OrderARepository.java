package fr.axl.lvy.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderARepository extends JpaRepository<OrderA, Long> {

  @Query("SELECT o FROM OrderA o LEFT JOIN FETCH o.client WHERE o.deletedAt IS NULL")
  List<OrderA> findByDeletedAtIsNull();
}
