package com.swproject.app.service;

import com.swproject.app.dto.CategoryDto;
import com.swproject.app.dto.HomeDto;
import com.swproject.app.entity.Expense;
import com.swproject.app.entity.ExpenseType;
import com.swproject.app.repository.ExpenseRepository;
import com.swproject.app.dto.HomeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor // final 필드를 매개변수로 받는 생성자를 자동 생성 (의존성 주입)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    // 전체 목록 조회 (최신 날짜 순)
    @Transactional(readOnly = true)
    public List<Expense> findAll() {
        return expenseRepository.findAllByOrderByExpenseDateDesc();
    }

    // 단건 조회 (상세/수정 화면용)
    @Transactional(readOnly = true)
    public Expense findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 기록을 찾을 수 없습니다. id=" + id));
    }

    // 등록
    @Transactional
    public Long save(Expense expense) {
        Expense saved = expenseRepository.save(expense);
        return saved.getId();
    }

    // 수정
    @Transactional
    public void update(Long id, Expense updateData) {
        Expense expense = findById(id);
        expense.setExpenseDate(updateData.getExpenseDate());
        expense.setAmount(updateData.getAmount());
        expense.setCategory(updateData.getCategory());
        expense.setType(updateData.getType());
        expense.setMemo(updateData.getMemo());
    }

    // 삭제
    @Transactional
    public void delete(Long id) {
        expenseRepository.deleteById(id);
    }

    // 홈 화면에 필요한 데이터
    @Transactional(readOnly = true)
    public HomeDto getHomeDto() {
        YearMonth thisMonth = YearMonth.now();
        LocalDate start = thisMonth.atDay(1);
        LocalDate end = thisMonth.atEndOfMonth();

        long expense = expenseRepository.sumAmountByTypeAndPeriod(ExpenseType.EXPENSE, start, end);
        long income = expenseRepository.sumAmountByTypeAndPeriod(ExpenseType.INCOME, start, end);

        // 수입이 0원이면 나누기 오류가 나니 방어 처리, 비율은 100%를 넘지 않게 고정
        int ratio = income == 0 ? 100 : (int) Math.min(100, Math.round(expense * 100.0 / income));

        List<Expense> recent = expenseRepository.findTop5ByOrderByExpenseDateDescCreatedAtDesc();

        List<Object[]> rawStats = expenseRepository.sumAmountByCategory(start, end);
        List<CategoryDto> categoryStats = rawStats.stream()
                .map(row -> {
                    String category = (String) row[0];
                    Long amount = (Long) row[1];
                    int percent = expense == 0 ? 0 : (int) Math.round(amount * 100.0 / expense);
                    return new CategoryDto(category, amount, percent);
                })
                .toList();

        return new HomeDto(expense, income, ratio, recent, categoryStats);
    }
}
