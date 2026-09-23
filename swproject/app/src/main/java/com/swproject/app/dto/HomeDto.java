package com.swproject.app.dto;

import com.swproject.app.entity.Expense;

import java.util.List;

public record HomeDto(
        long monthExpense,      // 이번 달 지출
        long monthIncome,       // 이번 달 수입
        int expenseRatio,       // 수입 대비 지출 비율 (진행 바 표시용)
        List<Expense> recent,   // 최근 내역
        List<CategoryDto> categoryStats // 카테고리별 지출
) {
}
