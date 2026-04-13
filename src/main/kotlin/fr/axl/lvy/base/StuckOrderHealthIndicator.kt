package fr.axl.lvy.base

import fr.axl.lvy.order.OrderCodig
import fr.axl.lvy.order.OrderCodigRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

/**
 * Reports WARN when CONFIRMED orders have not progressed to DELIVERED within [STUCK_THRESHOLD_DAYS]
 * days. An order that stays CONFIRMED for too long likely signals a delivery or invoicing
 * bottleneck.
 */
@Component
class StuckOrderHealthIndicator(private val orderCodigRepository: OrderCodigRepository) :
  HealthIndicator {

  override fun health(): Health {
    val threshold = Instant.now().minus(STUCK_THRESHOLD_DAYS, ChronoUnit.DAYS)
    val stuckCount =
      orderCodigRepository.countConfirmedNotUpdatedSince(
        OrderCodig.OrderCodigStatus.CONFIRMED,
        threshold,
      )
    return if (stuckCount == 0L) {
      Health.up().withDetail("confirmedOrdersOlderThan${STUCK_THRESHOLD_DAYS}Days", 0).build()
    } else {
      Health.status("WARN")
        .withDetail("confirmedOrdersOlderThan${STUCK_THRESHOLD_DAYS}Days", stuckCount)
        .build()
    }
  }

  companion object {
    private const val STUCK_THRESHOLD_DAYS = 30L
  }
}
