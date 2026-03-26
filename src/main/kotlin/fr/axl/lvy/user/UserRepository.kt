package fr.axl.lvy.user

import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
  fun findByEmail(email: String): Optional<User>
}
