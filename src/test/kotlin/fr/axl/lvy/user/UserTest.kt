package fr.axl.lvy.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class UserTest {

  @Autowired lateinit var userRepository: UserRepository

  @Test
  fun save_and_retrieve_user() {
    val user = User("Alice", "alice@example.com", "secret")
    userRepository.save(user)

    val found = userRepository.findById(user.id!!)
    assertThat(found).isPresent
    assertThat(found.get().name).isEqualTo("Alice")
    assertThat(found.get().email).isEqualTo("alice@example.com")
  }

  @Test
  fun findByEmail_returns_user() {
    val user = User("Bob", "bob@example.com", "secret")
    userRepository.save(user)

    val found = userRepository.findByEmail("bob@example.com")
    assertThat(found).isPresent
    assertThat(found.get().name).isEqualTo("Bob")
  }

  @Test
  fun findByEmail_returns_empty_for_unknown() {
    assertThat(userRepository.findByEmail("unknown@example.com")).isEmpty
  }

  @Test
  fun defaults_are_correct() {
    val user = User("Default", "default@example.com", "secret")
    assertThat(user.role).isEqualTo(User.Role.COLLABORATOR)
    assertThat(user.companyId).isNull()
    assertThat(user.active).isTrue
    assertThat(user.lastLogin).isNull()
  }

  @Test
  fun timestamps_set_on_persist() {
    val user = User("Timestamp", "ts@example.com", "secret")
    userRepository.save(user)

    assertThat(user.createdAt).isNotNull
    assertThat(user.updatedAt).isNotNull
  }

  @Test
  fun canSee_with_no_company_sees_all() {
    val user = User("NoCompany", "nocomp@example.com", "secret")
    user.companyId = null

    assertThat(user.canSee(User.Company.A)).isTrue
    assertThat(user.canSee(User.Company.B)).isTrue
    assertThat(user.canSee(User.Company.AB)).isTrue
  }

  @Test
  fun canSee_with_AB_company_sees_all() {
    val user = User("ABUser", "ab@example.com", "secret")
    user.companyId = User.Company.AB

    assertThat(user.canSee(User.Company.A)).isTrue
    assertThat(user.canSee(User.Company.B)).isTrue
    assertThat(user.canSee(User.Company.AB)).isTrue
  }

  @Test
  fun canSee_with_company_A_sees_A_and_AB() {
    val user = User("AUser", "a@example.com", "secret")
    user.companyId = User.Company.A

    assertThat(user.canSee(User.Company.A)).isTrue
    assertThat(user.canSee(User.Company.AB)).isTrue
    assertThat(user.canSee(User.Company.B)).isFalse
  }

  @Test
  fun canSee_with_company_B_sees_B_and_AB() {
    val user = User("BUser", "b@example.com", "secret")
    user.companyId = User.Company.B

    assertThat(user.canSee(User.Company.B)).isTrue
    assertThat(user.canSee(User.Company.AB)).isTrue
    assertThat(user.canSee(User.Company.A)).isFalse
  }

  @Test
  fun role_can_be_changed() {
    val user = User("Admin", "admin@example.com", "secret")
    user.role = User.Role.ADMIN
    userRepository.save(user)

    val found = userRepository.findById(user.id!!).orElseThrow()
    assertThat(found.role).isEqualTo(User.Role.ADMIN)
  }
}
