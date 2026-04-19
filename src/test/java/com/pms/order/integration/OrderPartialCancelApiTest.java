package com.pms.order.integration;

import com.pms.order.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("[통합] 주문 부분 취소 API")
class OrderPartialCancelApiTest extends IntegrationTestBase {

    private Long memberId;
    private Long orderId;
    private Long orderItem1Id;
    private Long orderItem2Id;

    @BeforeEach
    void setUp() {
        memberId = testDataHelper.createMember("partial-cancel@test.com", "PartialUser");
        Long categoryId = testDataHelper.createCategory("카테고리", null, 0, 1);
        Long productAId = testDataHelper.createProduct(categoryId, "상품A", BigDecimal.valueOf(10000), 100, "ON_SALE");
        Long productBId = testDataHelper.createProduct(categoryId, "상품B", BigDecimal.valueOf(20000), 100, "ON_SALE");

        // PAID 상태 주문을 직접 구성 (결제 경로 우회, 스펙 상 윈도우 내)
        orderId = testDataHelper.createOrder("ORD-TEST-PARTIAL-001", memberId, "PAID", BigDecimal.valueOf(40000));
        orderItem1Id = testDataHelper.createOrderItem(orderId, productAId, "상품A", BigDecimal.valueOf(10000), 2);
        orderItem2Id = testDataHelper.createOrderItem(orderId, productBId, "상품B", BigDecimal.valueOf(20000), 1);
        testDataHelper.createPayment(orderId, "PAY-TEST-PARTIAL-001", BigDecimal.valueOf(40000), "APPROVED");
    }

    @Test
    @DisplayName("부분 취소 시 PARTIALLY_CANCELLED 로 전이하고 환불액이 반환된다")
    void should_partially_cancel_order() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"orderItemId":%d,"quantity":1}]}
                                """.formatted(orderItem1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_CANCELLED"))
                .andExpect(jsonPath("$.cancelType").value("PARTIAL"))
                .andExpect(jsonPath("$.refundAmount").value(10000))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("PARTIALLY_CANCELLED");
    }

    @Test
    @DisplayName("빈 items 배열은 400 INVALID_CANCEL_REQUEST")
    void should_reject_empty_items() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 주문의 orderItemId 주입 시 403 ORDER_ITEM_NOT_IN_ORDER")
    void should_reject_idor() throws Exception {
        long foreignItemId = orderItem2Id + 9999L;
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"orderItemId":%d,"quantity":1}]}
                                """.formatted(foreignItemId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("중복 orderItemId 는 400 INVALID_CANCEL_REQUEST")
    void should_reject_duplicate_ids() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"orderItemId":%d,"quantity":1},
                                  {"orderItemId":%d,"quantity":1}
                                ]}
                                """.formatted(orderItem1Id, orderItem1Id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남은 수량 초과는 409 CANCEL_QUANTITY_EXCEEDS_REMAINING")
    void should_reject_exceeding_remaining() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"orderItemId":%d,"quantity":5}]}
                                """.formatted(orderItem1Id)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("body 없는 전체 취소는 하위호환으로 동작한다")
    void should_support_full_cancel_without_body() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header("X-Member-Id", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelType").value("FULL"));
    }
}
