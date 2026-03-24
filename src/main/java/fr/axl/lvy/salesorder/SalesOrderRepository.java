package fr.axl.lvy.salesorder;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

  List<SalesOrder> findByDeletedAtIsNull();
}
