package fr.axl.lvy.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ProductServiceTest {

  @Autowired ProductService productService;
  @Autowired ProductRepository productRepository;

  @Test
  void save_and_retrieve_product() {
    var product = new Product("REF-001", "Steel Beam");
    product.setSellingPriceExclTax(new BigDecimal("150.00"));
    product.setPurchasePriceExclTax(new BigDecimal("80.00"));
    product.setVatRate(new BigDecimal("20.00"));
    product.setUnit("kg");
    productService.save(product);

    var found = productService.findById(product.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getDesignation()).isEqualTo("Steel Beam");
    assertThat(found.get().getSellingPriceExclTax()).isEqualByComparingTo("150.00");
  }

  @Test
  void soft_delete_excludes_from_findAll() {
    var product = new Product("REF-DEL", "To Delete");
    productService.save(product);
    assertThat(productService.findAll()).anyMatch(p -> p.getReference().equals("REF-DEL"));

    productService.delete(product.getId());
    productRepository.flush();

    assertThat(productService.findAll()).noneMatch(p -> p.getReference().equals("REF-DEL"));
    // Still exists in DB
    assertThat(productService.findById(product.getId())).isPresent();
    assertThat(productService.findById(product.getId()).get().isDeleted()).isTrue();
  }

  @Test
  void findActive_excludes_inactive_products() {
    var active = new Product("REF-ACT", "Active Product");
    active.setActive(true);
    productService.save(active);

    var inactive = new Product("REF-INA", "Inactive Product");
    inactive.setActive(false);
    productService.save(inactive);

    var activeProducts = productService.findActive();
    assertThat(activeProducts)
        .anyMatch(p -> p.getReference().equals("REF-ACT"))
        .noneMatch(p -> p.getReference().equals("REF-INA"));
  }

  @Test
  void service_product_cannot_be_mto() {
    var product = new Product("REF-SVC", "Consulting");
    product.setType(Product.ProductType.SERVICE);
    product.setMto(true);
    productService.save(product);
    productRepository.flush();

    var found = productService.findById(product.getId()).orElseThrow();
    assertThat(found.isMto()).isFalse();
  }

  @Test
  void product_can_be_mto() {
    var product = new Product("REF-MTO", "Custom Part");
    product.setType(Product.ProductType.PRODUCT);
    product.setMto(true);
    productService.save(product);
    productRepository.flush();

    var found = productService.findById(product.getId()).orElseThrow();
    assertThat(found.isMto()).isTrue();
  }

  @Test
  void product_has_timestamps_after_persist() {
    var product = new Product("REF-TS", "Timestamped");
    productService.save(product);

    assertThat(product.getCreatedAt()).isNotNull();
    assertThat(product.getUpdatedAt()).isNotNull();
  }
}
