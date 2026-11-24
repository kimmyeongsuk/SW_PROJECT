package com.swproject.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 연도별 연말정산 입력값 (총급여, 부양 자녀 수). 연도당 1행
@Entity
@Table(name = "tax_profile")
@Getter @Setter @NoArgsConstructor
public class TaxProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tax_year", nullable = false, unique = true)
    private int year;

    @Column(name = "total_salary", nullable = false)
    private long totalSalary;      // 총급여 (연간, 원)

    @Column(nullable = false)
    private int children;          // 자녀 수 (카드 공제한도 계산용)
}