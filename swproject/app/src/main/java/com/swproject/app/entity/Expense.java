package com.swproject.app.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity //데이터베이스의 테이블과 1대 1 매핑되는 JPA인티티 선언
@Table(name = "expense") //DB에서의 테이블 이름 지정
@Getter
@Setter
@NoArgsConstructor //매개변수 없는 기본 생성자 자동 생성
public class Expense {

    @Id // 테이블 기본 키
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB의 AUTO_INCREMENT 기능
    private Long id;

    @NotNull(message = "날짜는 필수입니다.")//유효성 검사
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @NotNull(message = "금액은 필수입니다.")
    @Column(nullable = false) //NOT NUll 제약 조건 설정
    private Long amount; //지출, 수입

    @NotNull(message = "카테고리는 필수입니다.")
    @Column(length = 50, nullable = false)
    private String category;

    @Enumerated(EnumType.STRING) // DB에는 INCOME,EXPENSE 문자열로 그대로 저장
    @Column(length = 10, nullable = false)
    private ExpenseType type; //수입, 지출 구분

    @Column(length = 255)
    private String memo;

    @Column(name = "created_at", updatable = false) //등록 시간 수정 시  변경 보호
    private LocalDateTime createdAt;

    @PrePersist // 저장되기 직전에 자동으로 현재 시각을 채워줌
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
