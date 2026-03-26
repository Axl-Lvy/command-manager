package fr.axl.lvy.client.contact

import org.springframework.data.jpa.repository.JpaRepository

interface ContactRepository : JpaRepository<Contact, Long> {
  fun findByClientId(clientId: Long): List<Contact>
}
