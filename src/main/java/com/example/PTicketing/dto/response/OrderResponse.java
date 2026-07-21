package com.example.PTicketing.dto.response;

import com.example.PTicketing.enums.PaymentMethod;
import com.example.PTicketing.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderRef;
    private String eventTitle;
    private String eventSlug;
    private BigDecimal subtotal;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String currency;
    private String buyerEmail;
    private String buyerName;
    private String paystackUrl;
    private String discountCode;
    private BigDecimal discountAmount;
    private List<TicketResponse> tickets;
    private LocalDateTime createdAt;
}
