package com.orderhub.product;

import com.orderhub.common.dto.PageResponse;
import com.orderhub.common.exception.ConflictException;
import com.orderhub.common.exception.DuplicateResourceException;
import com.orderhub.common.exception.ResourceNotFoundException;
import com.orderhub.order.OrderItemRepository;
import com.orderhub.product.ProductDtos.ProductRequest;
import com.orderhub.product.ProductDtos.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final OrderItemRepository orderItems;

    public ProductService(ProductRepository products, OrderItemRepository orderItems) {
        this.products = products;
        this.orderItems = orderItems;
    }

    public ProductResponse create(ProductRequest request) {
        String sku = request.sku().trim();
        if (products.existsBySkuIgnoreCase(sku)) {
            throw new DuplicateResourceException("Product with SKU '%s' already exists".formatted(sku));
        }
        Product product = new Product();
        apply(product, request);
        return ProductResponse.from(products.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String search, Pageable pageable) {
        Page<Product> page = StringUtils.hasText(search)
                ? products.findByNameContainingIgnoreCase(search.trim(), pageable)
                : products.findAll(pageable);
        return PageResponse.from(page.map(ProductResponse::from));
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        String newSku = request.sku().trim();
        if (!product.getSku().equalsIgnoreCase(newSku) && products.existsBySkuIgnoreCase(newSku)) {
            throw new DuplicateResourceException("Product with SKU '%s' already exists".formatted(newSku));
        }
        apply(product, request);
        return ProductResponse.from(products.saveAndFlush(product));
    }

    public void delete(Long id) {
        Product product = find(id);
        if (orderItems.existsByProductId(id)) {
            throw new ConflictException("Product %d is referenced by existing orders and cannot be deleted".formatted(id));
        }
        products.delete(product);
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private static void apply(Product product, ProductRequest request) {
        product.setSku(request.sku().trim());
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
    }
}
