package com.swproject.app.dto;

import com.swproject.app.entity.Expense;

import java.util.List;

/**
 * 홈 화면 하나를 그리는 데 필요한 데이터를 한 번에 묶어서 전달하는 DTO.
 * Controller가 이걸 조립해서 Thymeleaf에 넘겨줍니다.
 */
public record HomeDto(
        long monthExpense,      // 이번 달 총 지출
        long monthIncome,       // 이번 달 총 수입
        int expenseRatio,       // 수입 대비 지출 비율(%) — 진행 바 표시용, 100 초과 시 100으로 고정
        List<Expense> recent,   // 최근 내역 5건
        List<CategoryDto> categoryStats // 카테고리별 지출 비중
) {
}
