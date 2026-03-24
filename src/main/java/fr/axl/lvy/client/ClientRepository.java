package fr.axl.lvy.client;

import fr.axl.lvy.user.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClientRepository extends JpaRepository<Client, Long> {

  List<Client> findByDeletedAtIsNull();

  @Query(
      "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND (c.visibleCompany = :company OR c.visibleCompany = 'AB')")
  List<Client> findVisibleFor(User.Company company);

  @Query(
      "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role IN ('CLIENT', 'BOTH') AND (c.visibleCompany = :company OR c.visibleCompany = 'AB')")
  List<Client> findClientsVisibleFor(User.Company company);

  @Query(
      "SELECT c FROM Client c WHERE c.deletedAt IS NULL AND c.role IN ('PRODUCER', 'BOTH') AND (c.visibleCompany = :company OR c.visibleCompany = 'AB')")
  List<Client> findProducersVisibleFor(User.Company company);
}
