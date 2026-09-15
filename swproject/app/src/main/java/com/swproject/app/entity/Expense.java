package com.swproject.app.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 지출/수입 기록 하나를 나타내는 엔티티.
 * 이 클래스 하나가 DB의 expense 테이블 한 줄(row)과 매핑됩니다.
 */
@Entity
@Table(name = "expense")
@Getter
@Setter
@NoArgsConstructor // JPA는 기본 생성자가 반드시 필요합니다.
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL의 AUTO_INCREMENT와 매핑
    private Long id;

    @NotNull(message = "날짜는 필수입니다.")
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @NotNull(message = "금액은 필수입니다.")
    @Column(nullable = false)
    private Long amount;

    @NotNull(message = "카테고리는 필수입니다.")
    @Column(length = 50, nullable = false)
    private String category;

    @Enumerated(EnumType.STRING) // DB에는 "INCOME"/"EXPENSE" 문자열로 저장됨
    @Column(length = 10, nullable = false)
    private ExpenseType type;

    @Column(length = 255)
    private String memo;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist // 저장되기 직전에 자동으로 현재 시각을 채워줌
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
