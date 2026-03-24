package fr.axl.lvy.product;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

  private final ProductRepository productRepository;

  ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Transactional(readOnly = true)
  public List<Product> findAll() {
    return productRepository.findByDeletedAtIsNull();
  }

  @Transactional(readOnly = true)
  public List<Product> findActive() {
    return productRepository.findByDeletedAtIsNullAndActiveTrue();
  }

  @Transactional(readOnly = true)
  public Optional<Product> findById(Long id) {
    return productRepository.findById(id);
  }

  @Transactional
  public Product save(Product product) {
    return productRepository.save(product);
  }

  @Transactional
  public void delete(Long id) {
    productRepository.findById(id).ifPresent(Product::softDelete);
  }
}
