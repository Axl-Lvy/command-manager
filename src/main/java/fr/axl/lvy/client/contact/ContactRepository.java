package fr.axl.lvy.client.contact;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

  List<Contact> findByClientId(Long clientId);
}
