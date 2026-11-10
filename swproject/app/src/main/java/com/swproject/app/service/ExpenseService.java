package com.swproject.app.service;

import com.swproject.app.dto.CategoryDto;
import com.swproject.app.dto.HomeDto;
import com.swproject.app.dto.DailyChartDto;
import com.swproject.app.entity.Expense;
import com.swproject.app.entity.ExpenseType;
import com.swproject.app.repository.ExpenseRepository;
import com.swproject.app.dto.HomeDto;
import com.swproject.app.dto.BillDto;
import com.swproject.app.dto.ForecastDto;
import com.swproject.app.dto.CategoryBarDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;
import com.swproject.app.dto.BillDto;
import com.swproject.app.dto.ForecastDto;
import com.swproject.app.dto.ImportResultDto;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

@Service
@RequiredArgsConstructor // final 필드를 매개변수로 받는 생성자를 자동 생성
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final SubscriptionService subscriptionService;   // (@RequiredArgsConstructor가 자동 주입)

    static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>() {{
        put("식비", List.of("김밥", "국밥", "식당", "분식", "맥도날드", "버거", "배달의민족", "요기요", "쿠팡이츠", "스타벅스", "카페", "커피", "이디야", "투썸"));
        put("교통", List.of("교통비","지하철", "버스", "택시", "카카오T", "교통카드", "주유", "주차"));
        put("쇼핑", List.of("쿠팡", "올리브영", "다이소", "마트", "이마트", "홈플러스", "무신사", "지마켓"));
        put("구독", List.of("넷플릭스", "유튜브", "왓챠", "멜론", "지니뮤직", "디즈니", "티빙", "웨이브", "클라우드","통신비", "SKT", "KT", "LG U+", "알뜰폰"));
        put("문화/여가", List.of("영화관", "CGV", "롯데시네마", "메가박스", "공연", "전시"));
        put("의료/건강", List.of("약국", "병원", "의원", "한의원"));
    }};


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
        subscriptionService.registerFromExpenses(List.of(saved)); //카테고리 구독이면 자동 등록
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
        subscriptionService.registerFromExpenses(List.of(expense)); //구독으로 바꾼 경우도 등록
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

        return new HomeDto(expense, income, ratio, recent, categoryStats,
                forecastMonth(thisMonth.plusMonths(1)));   // 다음 달 예상 지출


    }

    // 일별 지출 그래프에 필요한 데이터 조립
    @Transactional(readOnly = true)
    public DailyChartDto getDailyChart(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        // 지출: 날짜 --> 금액
        Map<LocalDate, Long> expenseByDate = new HashMap<>();
        for (Object[] row : expenseRepository.sumAmountByDate(start, end)) {
            expenseByDate.put((LocalDate) row[0], (Long) row[1]);
        }

        // 수입: 날짜 --> 금액
        Map<LocalDate, Long> incomeByDate = new HashMap<>();
        for (Object[] row : expenseRepository.sumIncomeByDate(start, end)) {
            incomeByDate.put((LocalDate) row[0], (Long) row[1]);
        }

        // ---- 카테고리별 막대 데이터 ----
        List<Object[]> rawCategory = expenseRepository.sumAmountByDateAndCategory(start, end);

        // 이번 달 카테고리별 총액을 구하고, 막대 높이의 기준(가장 큰 하루·카테고리 금액)도 찾음
        Map<String, Long> categoryTotals = new HashMap<>();
        long maxCategoryAmount = 1;
        for (Object[] row : rawCategory) {
            long amount = (Long) row[2];
            categoryTotals.merge((String) row[1], amount, Long::sum);
            maxCategoryAmount = Math.max(maxCategoryAmount, amount);
        }

        // 총액이 큰 카테고리부터 순서를 매김 --> 이 순서가 색상 번호가 됨
        List<String> legendCategories = categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        // 거래 내역: "날짜|카테고리" -->(말풍선에 보여줄 내용)
        Map<String, List<String>> itemsByDateCategory = new HashMap<>();
        for (Expense e : expenseRepository.findByExpenseDateBetweenOrderByExpenseDateAsc(start, end)) {
            if (e.getType() != ExpenseType.EXPENSE) continue;   // 수입은 제외
            String memo = (e.getMemo() == null || e.getMemo().isBlank()) ? "(메모 없음)" : e.getMemo();
            itemsByDateCategory
                    .computeIfAbsent(e.getExpenseDate() + "|" + e.getCategory(), k -> new java.util.ArrayList<>())
                    .add(memo + "  " + String.format(Locale.KOREAN, "%,d원", e.getAmount()));
        }

        // 날짜 --> 그날의 막대 목록
        Map<LocalDate, List<CategoryBarDto>> barsByDate = new HashMap<>();
        for (Object[] row : rawCategory) {
            LocalDate date = (LocalDate) row[0];
            String category = (String) row[1];
            long amount = (Long) row[2];
            int colorIndex = legendCategories.indexOf(category) % 8;
            int height = (int) Math.max(8, Math.round(amount * 100.0 / maxCategoryAmount));

            // 거래가 너무 많으면 8건까지만 보여주고 "외 N건"으로 줄임
            List<String> all = itemsByDateCategory.getOrDefault(date + "|" + category, List.of());
            List<String> items = all.size() <= 8 ? all : new java.util.ArrayList<>(all.subList(0, 8));
            if (all.size() > 8) items.add("외 " + (all.size() - 8) + "건");

            barsByDate.computeIfAbsent(date, k -> new java.util.ArrayList<>())
                    .add(new CategoryBarDto(category, amount, colorIndex, height, items));
        }

        // 1일부터 말일까지 빠짐없이 순회하며, 기록 없는 날은 0 / 빈 목록으로 채움
        int daysInMonth = month.lengthOfMonth();
        List<Integer> days = new java.util.ArrayList<>();
        List<Long> amounts = new java.util.ArrayList<>();
        List<Long> incomes = new java.util.ArrayList<>();
        List<List<CategoryBarDto>> dayBars = new java.util.ArrayList<>();
        long total = 0;
        long totalIncome = 0;

        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = month.atDay(d);
            long expense = expenseByDate.getOrDefault(date, 0L);
            long income = incomeByDate.getOrDefault(date, 0L);

            days.add(d);
            amounts.add(expense);
            incomes.add(income);
            dayBars.add(barsByDate.getOrDefault(date, List.of()));
            total += expense;
            totalIncome += income;
        }

        long average = daysInMonth == 0 ? 0 : Math.round(total / (double) daysInMonth);

        String monthLabel = month.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN));
        String prevMonth = month.minusMonths(1).toString();
        String nextMonth = month.plusMonths(1).toString();
        int startOffset = month.atDay(1).getDayOfWeek().getValue() % 7;

        // 정기구독 결제 예정일 (날짜 칸마다 표시)
        Map<Integer, List<BillDto>> billsByDay = subscriptionService.getBillsByDay(month);
        List<List<BillDto>> dayBills = new java.util.ArrayList<>();
        for (int d = 1; d <= daysInMonth; d++) {
            dayBills.add(billsByDay.getOrDefault(d, List.of()));   // 결제 없는 날은 빈 목록
        }

        return new DailyChartDto(monthLabel, days, amounts, incomes, dayBars, legendCategories,
                total, totalIncome, average, startOffset, prevMonth, nextMonth,
                dayBills, forecastMonth(month.plusMonths(1)));
    }

    // 기존 호출부 호환용: 등록된 건수만 돌려줌
    @Transactional
    public int importCsv(MultipartFile file) throws IOException {
        return importCsvDetailed(file).saved();
    }

    // 은행/카드 명세서 CSV를 읽어 등록하고, 등록/중복/건너뜀 결과를 돌려줌
    @Transactional
    public ImportResultDto importCsvDetailed(MultipartFile file) throws IOException {

        // 1) 인코딩 자동 감지(기존 decodeCsv 그대로) --> 헤더/구분자/형식 자동 판별 파싱
        String text = decodeCsv(file.getBytes());
        CsvStatementParser.Result parsed = CsvStatementParser.parse(text);

        // 2) 중복 방지: 파일의 날짜 범위에 해당하는 기존 내역을 "키 --> 개수"로 모아둠
        //    키 = 날짜 + 수입/지출 + 금액 + 내용
        Map<String, Integer> existing = new HashMap<>();
        if (!parsed.rows().isEmpty()) {
            LocalDate min = parsed.rows().stream().map(CsvStatementParser.Row::date).min(LocalDate::compareTo).get();
            LocalDate max = parsed.rows().stream().map(CsvStatementParser.Row::date).max(LocalDate::compareTo).get();
            for (Expense e : expenseRepository.findByExpenseDateBetweenOrderByExpenseDateAsc(min, max)) {
                existing.merge(dupKey(e.getExpenseDate(), e.getType(), e.getAmount(), e.getMemo()), 1, Integer::sum);
            }
        }

        int saved = 0;
        int duplicates = 0;
        List<Expense> importedExpenses = new java.util.ArrayList<>(); // 구독 자동 등록용

        for (CsvStatementParser.Row r : parsed.rows()) {
            String key = dupKey(r.date(), r.type(), r.amount(), r.desc());
            int left = existing.getOrDefault(key, 0);
            if (left > 0) {
                // 이미 있는 내역 --> 하나 소진시키고 건너뜀
                // (같은 파일 안에 똑같은 거래가 2건이면, DB에 이미 2건 있을 때만 둘 다 중복 처리됨)
                existing.put(key, left - 1);
                duplicates++;
                continue;
            }
            saved += saveImportedRow(r.date(), r.amount(), r.type(), r.desc(), importedExpenses);
        }

        // 업로드한 내역 중 카테고리가 "구독"인 것을 정기구독에 자동 등록 (기존 기능 유지)
        subscriptionService.registerFromExpenses(importedExpenses);

        // 3) 건너뛴 줄: 화면에는 최대 100줄까지만, 전체 개수는 따로 전달
        List<ImportResultDto.SkippedRow> skipped = parsed.skipped();
        List<ImportResultDto.SkippedRow> shown = skipped.size() <= 100 ? skipped : skipped.subList(0, 100);
        return new ImportResultDto(saved, duplicates, skipped.size(), shown);
    }

    // 중복 판별용 키
    private String dupKey(LocalDate date, ExpenseType type, long amount, String memo) {
        return date + "|" + type + "|" + amount + "|" + (memo == null ? "" : memo.trim());
    }

    // 파일 바이트를 보고 인코딩을 추정해서 문자열로 변환
    private String decodeCsv(byte[] bytes) {
        // 1) BOM(파일 맨 앞 표식)이 있으면 그걸 그대로 믿고, BOM 자체는 잘라냄
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }

        // 2) BOM이 없으면 UTF-8로 먼저 읽어봄. 깨진 글자가 하나라도 있으면 예외 --> UTF-8이 아님
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            // 3) UTF-8이 아니면 한국 은행 CSV에서 가장 흔한 CP949로 읽음
            //    (MS949는 EUC-KR에 없는 글자까지 포함. 환경에 없으면 EUC-KR로 대체)
            Charset cp949;
            try {
                cp949 = Charset.forName("MS949");
            } catch (Exception ex) {
                cp949 = Charset.forName("EUC-KR");
            }
            return new String(bytes, cp949);
        }
    }

    private int saveImportedRow(LocalDate date, long amount, ExpenseType type, String desc, List<Expense> collector) {
        Expense expense = new Expense();
        expense.setExpenseDate(date);
        expense.setAmount(amount);
        expense.setType(type);
        expense.setCategory(inferCategory(desc)); // 적요 기반으로 카테고리 자동 추정, 실패시 "미분류"
        expense.setMemo(desc);
        expenseRepository.save(expense);
        collector.add(expense);
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

    // "1,234,000", "31400.0" , "3.5E+6" 같은 콤마 포함 금액 문자열을 숫자로 변환. 빈 값이면 0 반환.
    private long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String cleaned = raw.replace(",", "").replace("원", "").replace("₩","").trim();
        try {
            // BigDecimal은 소수점·지수 표기를 모두 읽을 수 있음. 소수는 반올림해서 정수로 만듦
            return new java.math.BigDecimal(cleaned)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
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

    private String inferCategory(String description){
        if (description == null || description.isBlank()) return "미분류";
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (description.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "미분류";
    }

    @Transactional
    public List<Expense> findByCategory(String category) {
        return expenseRepository.findByCategory(category);
    }

    @Transactional
    public void saveAll(List<Expense> expenses) {
        expenseRepository.saveAll(expenses);
        subscriptionService.registerFromExpenses(expenses);
    }
    // ---------------------------------------------------------------
    // 다음 달 예상 지출 = 고정 지출 + 변동 지출
    //  - 고정 지출: 사용 중인 정기구독 월 합계
    //  - 변동 지출: 최근 최대 3개월의 변동 지출(총지출 - 고정성 지출)을 가중 이동평균
    //               (최근 달일수록 가중치가 큼)
    // ---------------------------------------------------------------

    private static final int[] WMA_WEIGHTS = {3, 2, 1};           // 가장 최근 달 --> 오래된 달
    private static final int MIN_DAYS_FOR_CURRENT_MONTH = 7;       // 이번 달은 7일 이상 지나야 예측에 사용
    // 정기구독으로 따로 계산하는 카테고리: 변동 지출에서 제외 (이중 계산 방지)
    private static final java.util.Set<String> FIXED_CATEGORIES = java.util.Set.of("구독", "통신");

    @Transactional(readOnly = true)
    public ForecastDto forecastMonth(YearMonth target) {
        long fixed = subscriptionService.getActiveMonthlyTotal();   // 고정 = 정기구독 합계
        long variable = weightedVariable(target);                   // 변동 = 가중 이동평균
        return new ForecastDto(fixed, variable, fixed + variable);
    }

    private long weightedVariable(YearMonth target) {
        LocalDate today = LocalDate.now();
        YearMonth nowMonth = YearMonth.from(today);
        List<String> subNames = subscriptionService.getActiveNames();

        double weightedSum = 0;
        double weightTotal = 0;
        int used = 0;   // 실제로 사용한 달 수 (가중치 순서 결정)

        // target 직전 3개월을 가까운 달부터 확인
        for (int i = 1; i <= WMA_WEIGHTS.length; i++) {
            YearMonth m = target.minusMonths(i);
            if (m.isAfter(nowMonth)) continue;   // 아직 시작 안 한 달은 데이터 없음

            boolean partial = m.equals(nowMonth);   // 아직 끝나지 않은 이번 달
            if (partial && today.getDayOfMonth() < MIN_DAYS_FOR_CURRENT_MONTH) continue; // 초반엔 너무 부정확

            long total = 0;
            long fixedPart = 0;
            for (Expense e : expenseRepository.findByExpenseDateBetweenOrderByExpenseDateAsc(
                    m.atDay(1), partial ? today : m.atEndOfMonth())) {
                if (e.getType() != ExpenseType.EXPENSE) continue;
                total += e.getAmount();
                if (isFixedExpense(e, subNames)) fixedPart += e.getAmount();
            }
            if (total == 0) continue;   // 기록 없는 달은 평균에 넣지 않음

            double monthVariable = total - fixedPart;   // 그 달의 변동 지출
            if (partial) {
                // 이번 달은 지금까지의 일평균으로 말일까지 환산
                monthVariable = monthVariable / today.getDayOfMonth() * m.lengthOfMonth();
            }

            int w = WMA_WEIGHTS[used];   // 사용 가능한 달 중 가장 최근부터 큰 가중치
            weightedSum += monthVariable * w;
            weightTotal += w;
            used++;
        }

        return weightTotal == 0 ? 0 : Math.round(weightedSum / weightTotal);
    }

    // 고정성 지출인가? (카테고리가 구독/통신이거나, 메모에 사용 중인 구독 이름이 들어 있음)
    private boolean isFixedExpense(Expense e, List<String> subNames) {
        if (FIXED_CATEGORIES.contains(e.getCategory())) return true;
        String memo = e.getMemo();
        if (memo == null || memo.isBlank()) return false;
        for (String name : subNames) {
            if (!name.isBlank() && memo.contains(name)) return true;
        }
        return false;
    }
}
