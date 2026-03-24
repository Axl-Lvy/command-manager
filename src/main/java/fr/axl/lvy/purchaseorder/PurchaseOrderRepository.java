package fr.axl.lvy.purchaseorder;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

  List<PurchaseOrder> findByDeletedAtIsNull();

  List<PurchaseOrder> findBySalesOrderId(Long salesOrderId);
}
