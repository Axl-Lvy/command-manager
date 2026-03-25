package fr.axl.lvy.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderBRepository extends JpaRepository<OrderB, Long> {

  @Query(
      "SELECT o FROM OrderB o LEFT JOIN FETCH o.orderA LEFT JOIN FETCH o.orderA.client WHERE o.deletedAt IS NULL")
  List<OrderB> findByDeletedAtIsNull();

  List<OrderB> findByOrderAId(Long orderAId);
}
