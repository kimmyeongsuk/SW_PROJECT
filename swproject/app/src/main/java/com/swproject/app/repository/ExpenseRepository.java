package com.swproject.app.repository;

import com.swproject.app.entity.Expense;
import com.swproject.app.entity.ExpenseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // 최신순 전체 조회 (목록 화면용)
    List<Expense> findAllByOrderByExpenseDateDesc();

    // 특정 기간(예: 이번 달) 지출 조회 — 일별 그래프 만들 때 사용 예정
    List<Expense> findByExpenseDateBetweenOrderByExpenseDateAsc(LocalDate start, LocalDate end);

    // 홈 화면 "최근 내역"용 — 최신 N건만
    List<Expense> findTop5ByOrderByExpenseDateDescCreatedAtDesc();

    // 이번 달 수입/지출 합계 — 홈 화면 요약 카드용
    @Query("select coalesce(sum(e.amount), 0) from Expense e " +
            "where e.type = :type and e.expenseDate between :start and :end")
    Long sumAmountByTypeAndPeriod(@Param("type") ExpenseType type,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    // 이번 달 카테고리별 지출 합계 — 홈 화면 도넛 차트용
    @Query("select e.category, sum(e.amount) " +
            "from Expense e " +
            "where e.type = com.swproject.app.entity.ExpenseType.EXPENSE " +
            "and e.expenseDate between :start and :end " +
            "group by e.category " +
            "order by sum(e.amount) desc")
    List<Object[]> sumAmountByCategory(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end);
}
