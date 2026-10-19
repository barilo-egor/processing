package net.rcetech.api.controller;

import net.rcetech.api.service.OrderApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

enum OrderEndpoint {
    LIST(false, HttpStatus.OK) {
        @Override
        MockHttpServletRequestBuilder request(String basePath, UUID orderId) {
            return get(basePath);
        }

        @Override
        void verifyCalled(OrderApiService orderApiService, UUID clientId, UUID orderId) {
            verify(orderApiService).findAll(eq(clientId), any(), any());
        }
    },
    GET(false, HttpStatus.OK) {
        @Override
        MockHttpServletRequestBuilder request(String basePath, UUID orderId) {
            return get(basePath + "/" + orderId);
        }

        @Override
        void verifyCalled(OrderApiService orderApiService, UUID clientId, UUID orderId) {
            verify(orderApiService).findByIdAndClientId(clientId, orderId);
        }
    },
    CREATE(true, HttpStatus.CREATED) {
        @Override
        MockHttpServletRequestBuilder request(String basePath, UUID orderId) {
            return post(basePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "internalId": "%s",
                              "amount": 1500,
                              "methods": ["CARD", "SBP"],
                              "callbackUrl": "https://merchant.example.com/callback"
                            }
                            """.formatted(orderId));
        }

        @Override
        void verifyCalled(OrderApiService orderApiService, UUID clientId, UUID orderId) {
            verify(orderApiService).createOrder(eq(clientId), any(), any(), anyBoolean());
        }
    },
    CANCEL(true, HttpStatus.OK) {
        @Override
        MockHttpServletRequestBuilder request(String basePath, UUID orderId) {
            return patch(basePath + "/" + orderId);
        }

        @Override
        void verifyCalled(OrderApiService orderApiService, UUID clientId, UUID orderId) {
            verify(orderApiService).cancelOrder(clientId, orderId);
        }
    };

    private final boolean modifying;

    private final HttpStatus successStatus;

    OrderEndpoint(boolean modifying, HttpStatus successStatus) {
        this.modifying = modifying;
        this.successStatus = successStatus;
    }

    abstract MockHttpServletRequestBuilder request(String basePath, UUID orderId);

    abstract void verifyCalled(OrderApiService orderApiService, UUID clientId, UUID orderId);

    boolean isModifying() {
        return modifying;
    }

    int successStatus() {
        return successStatus.value();
    }
}
