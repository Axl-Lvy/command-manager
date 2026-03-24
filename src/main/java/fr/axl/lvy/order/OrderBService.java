package fr.axl.lvy.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderBService {

  private static final Set<OrderB.OrderBStatus> ALLOWED_TRANSITIONS_FROM_SENT =
      Set.of(OrderB.OrderBStatus.CONFIRMED, OrderB.OrderBStatus.CANCELLED);
  private static final Set<OrderB.OrderBStatus> ALLOWED_TRANSITIONS_FROM_CONFIRMED =
      Set.of(OrderB.OrderBStatus.IN_PRODUCTION, OrderB.OrderBStatus.CANCELLED);
  private static final Set<OrderB.OrderBStatus> ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION =
      Set.of(OrderB.OrderBStatus.RECEIVED, OrderB.OrderBStatus.CANCELLED);

  private final OrderBRepository orderBRepository;

  OrderBService(OrderBRepository orderBRepository) {
    this.orderBRepository = orderBRepository;
  }

  @Transactional(readOnly = true)
  public List<OrderB> findAll() {
    return orderBRepository.findByDeletedAtIsNull();
  }

  @Transactional(readOnly = true)
  public Optional<OrderB> findById(Long id) {
    return orderBRepository.findById(id);
  }

  @Transactional
  public OrderB save(OrderB order) {
    return orderBRepository.save(order);
  }

  @Transactional
  public void delete(Long id) {
    orderBRepository.findById(id).ifPresent(OrderB::softDelete);
  }

  @Transactional
  public OrderB changeStatus(OrderB order, OrderB.OrderBStatus newStatus) {
    var allowed = getAllowedTransitions(order.getStatus());
    if (!allowed.contains(newStatus)) {
      throw new IllegalStateException(
          "Cannot transition from " + order.getStatus() + " to " + newStatus);
    }
    order.setStatus(newStatus);
    return orderBRepository.save(order);
  }

  @Transactional
  public OrderB markReceived(
      OrderB order, LocalDate receptionDate, boolean conforming, String receptionReserve) {
    order.setReceptionDate(receptionDate);
    order.setReceptionConforming(conforming);
    order.setReceptionReserve(receptionReserve);
    order.setStatus(OrderB.OrderBStatus.RECEIVED);
    return orderBRepository.save(order);
  }

  private Set<OrderB.OrderBStatus> getAllowedTransitions(OrderB.OrderBStatus current) {
    return switch (current) {
      case SENT -> ALLOWED_TRANSITIONS_FROM_SENT;
      case CONFIRMED -> ALLOWED_TRANSITIONS_FROM_CONFIRMED;
      case IN_PRODUCTION -> ALLOWED_TRANSITIONS_FROM_IN_PRODUCTION;
      default -> Set.of();
    };
  }
}
