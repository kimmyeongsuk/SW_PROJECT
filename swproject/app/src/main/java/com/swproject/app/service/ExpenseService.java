package com.swproject.app.service;

import com.swproject.app.dto.CategoryDto;
import com.swproject.app.dto.HomeDto;
import com.swproject.app.dto.DailyChartDto;
import com.swproject.app.entity.Expense;
import com.swproject.app.entity.ExpenseType;
import com.swproject.app.repository.ExpenseRepository;
import com.swproject.app.dto.HomeDto;
import com.swproject.app.dto.CategoryBarDto;
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

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>() {{
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

        return new DailyChartDto(monthLabel, days, amounts, incomes, dayBars, legendCategories,
                total, totalIncome, average, startOffset, prevMonth, nextMonth);
    }

    // 은행 명세서(CSV)를 업로드해서 지출/수입 기록을 일괄 등록
    // 은행마다 컬럼명이 달라서, 자주 쓰이는 이름들을 후보로 두고 찾아서 매칭
    @Transactional
    public int importCsv(MultipartFile file) throws IOException {

        // 은행별로 다른 컬럼명 후보들 (여기 없는 은행이면 이 리스트에 이름만 추가하면 됨)
        List<String> dateCols = List.of("거래일시", "거래일자", "이용일자", "날짜");
        List<String> withdrawCols = List.of("출금액", "출금", "이용금액");
        List<String> depositCols = List.of("입금액", "입금");
        List<String> descCols = List.of("적요", "내용", "가맹점명", "거래내용", "메모");

        int savedCount = 0;

        // 인코딩을 자동으로 감지해서 문자열로 변환
        String text = decodeCsv(file.getBytes());

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
                .parse(new StringReader(text))) {

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

    private int saveImportedRow(LocalDate date, long amount, ExpenseType type, String desc) {
        Expense expense = new Expense();
        expense.setExpenseDate(date);
        expense.setAmount(amount);
        expense.setType(type);
        expense.setCategory(inferCategory(desc)); // 적요 기반으로 카테고리 자동 추정, 실패시 "미분류"
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
    }
}
