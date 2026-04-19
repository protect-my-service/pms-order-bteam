package com.pms.order.domain.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelOrderRequest {

    @Size(max = 500)
    private String reason;

    @Valid
    private List<CancelItem> items;

    public boolean hasItems() {
        return items != null;
    }

    public boolean isEmptyItems() {
        return items != null && items.isEmpty();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancelItem {
        @NotNull
        private Long orderItemId;

        @Min(1)
        private int quantity;
    }
}
