package com.swproject.app.repository;

import com.swproject.app.entity.Expense;
import com.swproject.app.entity.ExpenseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    //최신순 전체 조회 (목록)
    //날짜 기준 최신순으로 가져옴
    List<Expense> findAllByOrderByExpenseDateDesc();

    //특정 기간 지출 조회(일별 그래프)
    List<Expense> findByExpenseDateBetweenOrderByExpenseDateAsc(LocalDate start, LocalDate end);

    //홈 화면(최근 N건 내역만)
    //최근 지출/수입 데이터 5개만 추출
    List<Expense> findTop5ByOrderByExpenseDateDescCreatedAtDesc();

    //이번 달 수입/지출 합계
    @Query("select coalesce(sum(e.amount), 0) from Expense e " +
            "where e.type = :type and e.expenseDate between :start and :end")
    Long sumAmountByTypeAndPeriod(@Param("type") ExpenseType type,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    // 이번 달 카테고리별 지출 합계(카테고리별 그래프)
    //각 카테고리별로 묶어서 지출 총액 구해서 내림차순으로 정렬
    @Query("select e.category, sum(e.amount) " +
            "from Expense e " +
            "where e.type = com.swproject.app.entity.ExpenseType.EXPENSE " +
            "and e.expenseDate between :start and :end " +
            "group by e.category " +
            "order by sum(e.amount) desc")
    List sumAmountByCategory(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    // 일별 지출 그래프용 — 날짜별 지출 합계 (해당 날짜에 기록이 없으면 결과에서 아예 빠짐)
    @Query("select e.expenseDate, sum(e.amount) " +
            "from Expense e " +
            "where e.type = com.swproject.app.entity.ExpenseType.EXPENSE " +
            "and e.expenseDate between :start and :end " +
            "group by e.expenseDate " +
            "order by e.expenseDate asc")
    List<Object[]> sumAmountByDate(@Param("start") LocalDate start,
                                   @Param("end") LocalDate end);
}

