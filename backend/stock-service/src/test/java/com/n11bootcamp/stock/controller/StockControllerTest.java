package com.n11bootcamp.stock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11bootcamp.stock.config.SecurityConfig;
import com.n11bootcamp.stock.dto.StockResponse;
import com.n11bootcamp.stock.dto.UpdateStockRequest;
import com.n11bootcamp.stock.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import(SecurityConfig.class)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StockService stockService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getStock_withoutAuthentication_returns200() throws Exception {
        StockResponse response = new StockResponse(101L, 50, 5);
        when(stockService.getStock(101L)).thenReturn(response);

        mockMvc.perform(get("/api/stocks/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(101L))
                .andExpect(jsonPath("$.availableQuantity").value(50));
    }

    @Test
    void getStock_withAuthentication_returns200() throws Exception {
        StockResponse response = new StockResponse(101L, 50, 5);
        when(stockService.getStock(101L)).thenReturn(response);

        mockMvc.perform(get("/api/stocks/101")
                        .with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void setStock_whenClientAdmin_returns200() throws Exception {
        UpdateStockRequest request = new UpdateStockRequest();
        request.setProductId(101L);
        request.setQuantity(100);

        StockResponse response = new StockResponse(101L, 100, 0);
        when(stockService.setStock(any(UpdateStockRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/stocks")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(100));
    }

    @Test
    void setStock_whenNotAdmin_returns403() throws Exception {
        UpdateStockRequest request = new UpdateStockRequest();
        request.setProductId(101L);
        request.setQuantity(100);

        mockMvc.perform(put("/api/stocks")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setStock_whenNotAuthenticated_returns401() throws Exception {
        UpdateStockRequest request = new UpdateStockRequest();
        request.setProductId(101L);
        request.setQuantity(100);

        mockMvc.perform(put("/api/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void increaseStock_whenClientAdmin_returns200() throws Exception {
        StockResponse response = new StockResponse(101L, 60, 0);
        when(stockService.increaseStock(eq(101L), eq(10))).thenReturn(response);

        mockMvc.perform(patch("/api/stocks/101/increase")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN")))
                        .param("delta", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(60));
    }

    @Test
    void increaseStock_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(patch("/api/stocks/101/increase")
                        .with(jwt())
                        .param("delta", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void decreaseStock_whenClientAdmin_returns200() throws Exception {
        StockResponse response = new StockResponse(101L, 40, 0);
        when(stockService.decreaseStock(eq(101L), eq(10))).thenReturn(response);

        mockMvc.perform(patch("/api/stocks/101/decrease")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN")))
                        .param("delta", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(40));
    }

    @Test
    void decreaseStock_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(patch("/api/stocks/101/decrease")
                        .with(jwt())
                        .param("delta", "10"))
                .andExpect(status().isForbidden());
    }
}
