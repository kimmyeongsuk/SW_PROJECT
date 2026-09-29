package com.swproject.app.service;

import com.swproject.app.dto.CategoryDto;
import com.swproject.app.dto.HomeDto;
import com.swproject.app.dto.DailyChartDto;
import com.swproject.app.entity.Expense;
import com.swproject.app.entity.ExpenseType;
import com.swproject.app.repository.ExpenseRepository;
import com.swproject.app.dto.HomeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
    // 일별 지출 그래프에 필요한 데이터 조립
    @Transactional(readOnly = true)
    public DailyChartDto getDailyChart(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        // DB에는 "기록이 있는 날짜"만 결과로 나오므로, 날짜(LocalDate) → 금액 맵으로 바꿔둠
        List<Object[]> raw = expenseRepository.sumAmountByDate(start, end);
        Map<LocalDate, Long> amountByDate = new HashMap<>();
        for (Object[] row : raw) {
            amountByDate.put((LocalDate) row[0], (Long) row[1]);
        }

        // 1일부터 말일까지 빠짐없이 순회하면서, 기록 없는 날은 0원으로 채워 넣음
        int daysInMonth = month.lengthOfMonth();
        List<Integer> days = new java.util.ArrayList<>();
        List<Long> amounts = new java.util.ArrayList<>();
        long total = 0;

        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = month.atDay(d);
            long amount = amountByDate.getOrDefault(date, 0L);
            days.add(d);
            amounts.add(amount);
            total += amount;
        }

        long average = daysInMonth == 0 ? 0 : Math.round(total / (double) daysInMonth);

        String monthLabel = month.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN));
        String prevMonth = month.minusMonths(1).toString();
        String nextMonth = month.plusMonths(1).toString();

        return new DailyChartDto(monthLabel, days, amounts, total, average, prevMonth, nextMonth);
    }

    // 은행 명세서(CSV)를 업로드해서 지출/수입 기록을 일괄 등록
    // 은행마다 컬럼명이 달라서, 자주 쓰이는 이름들을 후보로 두고 찾아서 매칭합니다.
    @Transactional
    public int importCsv(MultipartFile file) throws IOException {

        // 은행별로 다른 컬럼명 후보들 (여기 없는 은행이면 이 리스트에 이름만 추가하면 됨)
        List<String> dateCols = List.of("거래일시", "거래일자", "이용일자", "날짜");
        List<String> withdrawCols = List.of("출금액", "출금", "이용금액");
        List<String> depositCols = List.of("입금액", "입금");
        List<String> descCols = List.of("적요", "내용", "가맹점명", "거래내용", "메모");

        int savedCount = 0;

        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()               // 첫 줄을 컬럼명으로 인식
                     .setSkipHeaderRecord(true) // 첫 줄은 데이터로 취급하지 않음
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            // 실제 파일의 헤더 중에서, 위 후보 이름과 일치하는 걸 찾아 실제 컬럼명 확정
            var headers = parser.getHeaderMap().keySet();
            String dateCol = findMatchingColumn(headers, dateCols);
            String withdrawCol = findMatchingColumn(headers, withdrawCols);
            String depositCol = findMatchingColumn(headers, depositCols);
            String descCol = findMatchingColumn(headers, descCols);

            if (dateCol == null) {
                throw new IllegalArgumentException("날짜 컬럼을 찾을 수 없습니다. CSV 헤더를 확인해주세요.");
            }

            for (CSVRecord row : parser) {
                LocalDate date = parseFlexibleDate(row.get(dateCol));
                if (date == null) continue; // 날짜를 못 읽으면 그 줄은 건너뜀

                String desc = (descCol != null) ? row.get(descCol) : "";

                // 출금액이 있으면 지출로 등록
                if (withdrawCol != null) {
                    long amount = parseAmount(row.get(withdrawCol));
                    if (amount > 0) {
                        savedCount += saveImportedRow(date, amount, ExpenseType.EXPENSE, desc);
                    }
                }
                // 입금액이 있으면 수입으로 등록
                if (depositCol != null) {
                    long amount = parseAmount(row.get(depositCol));
                    if (amount > 0) {
                        savedCount += saveImportedRow(date, amount, ExpenseType.INCOME, desc);
                    }
                }
            }
        }

        return savedCount;
    }

    private int saveImportedRow(LocalDate date, long amount, ExpenseType type, String desc) {
        Expense expense = new Expense();
        expense.setExpenseDate(date);
        expense.setAmount(amount);
        expense.setType(type);
        expense.setCategory("미분류"); // 카테고리는 명세서에 없으므로 일단 미분류로 등록, 나중에 직접 수정
        expense.setMemo(desc);
        expenseRepository.save(expense);
        return 1;
    }

    // 후보 이름 목록 중 실제 CSV 헤더에 있는 걸 찾아서 반환 (없으면 null)
    private String findMatchingColumn(java.util.Set<String> headers, List<String> candidates) {
        for (String candidate : candidates) {
            for (String header : headers) {
                if (header.replace(" ", "").equals(candidate)) {
                    return header;
                }
            }
        }
        return null;
    }

    // "1,234,000" 같은 콤마 포함 금액 문자열을 숫자로 변환. 빈 값이면 0 반환.
    private long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String cleaned = raw.replace(",", "").replace("원", "").trim();
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // "2026-09-05", "2026.09.05", "2026-09-05 13:22:10" 등 여러 형식을 시도해서 날짜만 추출
    private LocalDate parseFlexibleDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String datePart = raw.trim().split(" ")[0]; // 시간이 붙어있으면 날짜 부분만 자름
        String[] patterns = { "yyyy-MM-dd", "yyyy.MM.dd", "yyyy/MM/dd", "yyyyMMdd" };
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(datePart, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // 다음 패턴 시도
            }
        }
        return null;
    }
}
