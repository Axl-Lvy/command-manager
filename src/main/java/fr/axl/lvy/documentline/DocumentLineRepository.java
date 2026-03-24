package fr.axl.lvy.documentline;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentLineRepository extends JpaRepository<DocumentLine, Long> {

  List<DocumentLine> findByDocumentTypeAndDocumentIdOrderByPosition(
      DocumentLine.DocumentType documentType, Long documentId);
}
