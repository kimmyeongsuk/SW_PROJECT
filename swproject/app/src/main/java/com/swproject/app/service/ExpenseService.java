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
@RequiredArgsConstructor // final 필드를 매개변수로 받는 생성자를 자동 생성
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    //전체 목록 조회 (최신 날짜 순)
    @Transactional(readOnly = true) //읽기정용 트랜잭션
    public List<Expense> findAll() {
        // 지출/수입 전체 내역을 날짜순으로 가져옴
        return expenseRepository.findAllByOrderByExpenseDateDesc();
    }

    //단건 조회
    @Transactional(readOnly = true)
    public Expense findById(Long id) {
        //ID로 DB조회 후 데이터가 없으면 예외처리
        return expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 기록을 찾을 수 없습니다. id=" + id));
    }

    //신규 등록
    @Transactional //쓰기 권한 가지는 트랜잭션
    public Long save(Expense expense) {
        //Expense 객체를 DB에 저장하고 id를 반환받음
        Expense saved = expenseRepository.save(expense);
        return saved.getId();
    }

    //수정
    @Transactional //에 의해자동으로 DB 반영
    public void update(Long id, Expense updateData) {
        //수정할 기존 데이터 DB 조회
        Expense expense = findById(id);

        //조회해 온 기존 객제의 값을 새로 입력받는 값으로 변경
        expense.setExpenseDate(updateData.getExpenseDate());
        expense.setAmount(updateData.getAmount());
        expense.setCategory(updateData.getCategory());
        expense.setType(updateData.getType());
        expense.setMemo(updateData.getMemo());
    }

    //삭제
    @Transactional
    public void delete(Long id) {
        // 받은 id값에 해당하는 내역 DB 삭제
        expenseRepository.deleteById(id);
    }

    // 홈 화면에 필요한 데이터(시간,비율, 총 지출/수입액)
    @Transactional(readOnly = true)
    public HomeDto getHomeDto() {
        //현재 년월
        YearMonth thisMonth = YearMonth.now();
        //이번 달 시작일
        LocalDate start = thisMonth.atDay(1);
        //이번 달 마지막일
        LocalDate end = thisMonth.atEndOfMonth();

        //DB에서 총 지출/수입액을 계산 후 가져옴
        long expense = expenseRepository.sumAmountByTypeAndPeriod(ExpenseType.EXPENSE, start, end);
        long income = expenseRepository.sumAmountByTypeAndPeriod(ExpenseType.INCOME, start, end);

        int ratio; // 지출 비율

        if (income == 0) {
            ratio = 100;
        } else {
            double rawRatio = expense * 100.0 / income;

            //소수점 반올림 처리, 최대 100%까지 제한
            long roundRatio = Math.round(rawRatio);
            long finalRatio = Math.min(100, roundRatio);

            ratio = (int) finalRatio;
        }

        //최근 5건의 내역 가져옴
        List<Expense> recent = expenseRepository.findTop5ByOrderByExpenseDateDescCreatedAtDesc();

        List<Object[]> rawStats = expenseRepository.sumAmountByCategory(start, end);

        //
        List<CategoryDto> categoryStats = rawStats.stream()
                .map(row -> {
                    String category = (String) row[0];  //카테고리명
                    Long amount = (Long) row[1];        //해당 카테고리 총 금액

                    //카테고리별 전체 지출 비율
                    int percent;

                    if (expense == 0) {
                        percent = 0;
                    } else {
                        //비율 계산
                        double calculated = (double) amount * 100.0 / expense;
                        long rounded = Math.round(calculated);
                        percent = (int) rounded;
                    }
                    return new CategoryDto(category, amount, percent);
                })
                .toList(); //변환 값을 list 컬렉션으로 묶음

        return new HomeDto(expense, income, ratio, recent, categoryStats);
    }
}
