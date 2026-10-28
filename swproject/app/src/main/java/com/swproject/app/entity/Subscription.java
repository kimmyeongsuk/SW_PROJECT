package com.swproject.app.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정기구독·고정지출 항목 하나 (넷플릭스, 통신비, 월세 등) */
@Entity
@Table(name = "subscription")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "항목 이름은 필수입니다.")
    @Column(length = 100, nullable = false)
    private String name;

    @NotNull(message = "월 결제액은 필수입니다.")
    @Positive(message = "금액은 0보다 커야 합니다.")
    @Column(nullable = false)
    private Long amount;

    @NotNull(message = "결제일은 필수입니다.")
    @Min(value = 1, message = "결제일은 1일 이상이어야 합니다.")
    @Max(value = 31, message = "결제일은 31일 이하여야 합니다.")
    @Column(name = "billing_day", nullable = false)
    private Integer billingDay;

    @NotBlank(message = "카테고리는 필수입니다.")
    @Column(length = 30, nullable = false)
    private String category;

    // true = 사용 중, false = 해지 (삭제하지 않고 기록은 남김)
    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 255)
    private String memo;
}