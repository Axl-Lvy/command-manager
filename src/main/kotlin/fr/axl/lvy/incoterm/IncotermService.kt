package fr.axl.lvy.incoterm

import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages the incoterm reference table. Enforces name uniqueness (case-insensitive). */
@Service
class IncotermService(private val incotermRepository: IncotermRepository) {

  @Transactional(readOnly = true)
  fun findAll(): List<Incoterm> = incotermRepository.findAllByOrderByNameAsc()

  /** Paginated fetch for Vaadin lazy-loading grids. */
  @Transactional(readOnly = true)
  fun findAll(pageable: Pageable): Page<Incoterm> = incotermRepository.findAll(pageable)

  @Transactional(readOnly = true)
  fun findById(id: Long): Optional<Incoterm> = incotermRepository.findById(id)

  @Transactional
  fun save(incoterm: Incoterm): Incoterm {
    incoterm.name = incoterm.name.trim().uppercase()
    incoterm.label = incoterm.label.trim()
    val existing = incotermRepository.findByNameIgnoreCase(incoterm.name)
    require(!(existing.isPresent && existing.get().id != incoterm.id)) {
      "Un incoterm avec le nom '${incoterm.name}' existe déjà"
    }
    return incotermRepository.save(incoterm)
  }

  @Transactional
  fun delete(id: Long) {
    if (incotermRepository.existsById(id)) {
      incotermRepository.deleteById(id)
    }
  }
}
