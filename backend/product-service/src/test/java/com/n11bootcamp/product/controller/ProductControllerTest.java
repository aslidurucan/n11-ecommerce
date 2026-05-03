package com.n11bootcamp.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11bootcamp.product.dto.CreateProductRequest;
import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.exception.ProductNotFoundException;
import com.n11bootcamp.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getProduct_whenProductExists_returns200WithBody() throws Exception {
        ProductResponse response = buildResponse(1L, "iPhone", "Elektronik");
        when(productService.getProduct(eq(1L), any())).thenReturn(response);

        mockMvc.perform(get("/api/products/1")
                        .header("Accept-Language", "tr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone"))
                .andExpect(jsonPath("$.category").value("Elektronik"));
    }

    @Test
    void getProduct_whenProductNotFound_returns404() throws Exception {
        when(productService.getProduct(eq(99L), any()))
                .thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/products/99")
                        .header("Accept-Language", "tr"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProducts_withNoFilter_returns200WithPage() throws Exception {
        ProductResponse response = buildResponse(1L, "iPhone", "Elektronik");
        when(productService.listProducts(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/products")
                        .header("Accept-Language", "tr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("iPhone"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listProducts_withCategoryParam_passesFilterToService() throws Exception {
        when(productService.listProducts(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/products")
                        .param("category", "Elektronik")
                        .param("minPrice", "100")
                        .header("Accept-Language", "tr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void createProduct_whenValidRequest_returns201WithBody() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                "Elektronik", "Apple",
                new BigDecimal("1000.00"), "http://img.com/i.jpg",
                List.of(new CreateProductRequest.TranslationRequest("tr", "iPhone", "Akıllı telefon"))
        );
        ProductResponse response = buildResponse(1L, "iPhone", "Elektronik");
        when(productService.createProduct(any())).thenReturn(response);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone"));
    }

    @Test
    void createProduct_whenMissingCategory_returns400() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                null, "Apple",
                new BigDecimal("1000.00"), null,
                List.of(new CreateProductRequest.TranslationRequest("tr", "iPhone", null))
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteProduct_whenProductExists_returns204() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProduct_whenProductNotFound_returns404() throws Exception {
        doThrow(new ProductNotFoundException(99L)).when(productService).deleteProduct(99L);

        mockMvc.perform(delete("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    private ProductResponse buildResponse(Long id, String name, String category) {
        return ProductResponse.builder()
                .id(id)
                .name(name)
                .category(category)
                .basePrice(new BigDecimal("1000.00"))
                .active(true)
                .build();
    }
}
