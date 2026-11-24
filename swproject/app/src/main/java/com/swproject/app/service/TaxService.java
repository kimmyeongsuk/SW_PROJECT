package com.swproject.app.service;

import com.swproject.app.dto.TaxSummaryDto;
import com.swproject.app.entity.*;
import com.swproject.app.repository.ExpenseRepository;
import com.swproject.app.repository.TaxProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaxService {

    private final ExpenseRepository expenseRepository;
    private final TaxProfileRepository profileRepository;

    // 카드 공제 대상이 아닌 카테고리 (필요하면 추가/삭제). 경조사는 요청대로 별도 처리 안 함
    private static final Set<String> EXCLUDED_CATEGORIES = Set.of("통신", "보험", "관리비", "월세", "세금");

    @Transactional
    public void saveProfile(int year, long salary, int children) {
        TaxProfile p = profileRepository.findByYear(year).orElseGet(TaxProfile::new);
        p.setYear(year);
        p.setTotalSalary(Math.max(0, salary));
        p.setChildren(Math.max(0, children));
        profileRepository.save(p);
    }

    @Transactional(readOnly = true)
    public TaxSummaryDto getSummary(int year) {
        TaxProfile profile = profileRepository.findByYear(year).orElse(null);
        long salary = profile == null ? 0 : profile.getTotalSalary();
        int children = profile == null ? 0 : profile.getChildren();

        // 해당 연도 전체 내역 중 지출만
        // (ExpenseRepository에 findByExpenseDateBetween이 이미 있으면 그걸 쓰세요 — 아래 3번 참고)
        List<Expense> list = expenseRepository
                .findByExpenseDateBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

        long credit = 0, debit = 0, cash = 0, unspecified = 0, excluded = 0;
        for (Expense e : list) {
            if (e.getType() != ExpenseType.EXPENSE) continue;
            if (isExcluded(e)) { excluded += e.getAmount(); continue; }
            PaymentMethod pm = e.getPaymentMethod();
            if (pm == null || pm == PaymentMethod.UNSPECIFIED) unspecified += e.getAmount();
            else switch (pm) {
                case CREDIT -> credit += e.getAmount();
                case DEBIT -> debit += e.getAmount();
                case CASH_RECEIPT -> cash += e.getAmount();
                default -> { }
            }
        }

        TaxCalculator.Result now = TaxCalculator.calculate(salary, children, credit, debit + cash);

        // 연말 예상: 오늘까지 경과일로 연간 환산 (과거 연도면 그대로)
        LocalDate today = LocalDate.now();
        double factor = 1.0;
        if (today.getYear() == year) {
            factor = (double) LocalDate.of(year, 12, 31).getDayOfYear() / Math.max(1, today.getDayOfYear());
        }
        TaxCalculator.Result projected = TaxCalculator.calculate(salary, children,
                Math.round(credit * factor), Math.round((debit + cash) * factor));

        return new TaxSummaryDto(year, salary, children, credit, debit, cash,
                unspecified, excluded, now, projected, profile != null);
    }

    // 카테고리가 제외 목록이거나, 메모가 "KT"로 시작(통신사)하되 KTX는 아닌 경우
    private boolean isExcluded(Expense e) {
        if (EXCLUDED_CATEGORIES.contains(e.getCategory())) return true;
        String memo = e.getMemo() == null ? "" : e.getMemo().toUpperCase();
        return memo.startsWith("KT") && !memo.startsWith("KTX");
    }
}