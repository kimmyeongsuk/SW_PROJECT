package com.swproject.app.service;

import com.swproject.app.dto.ImportResultDto.SkippedRow;
import com.swproject.app.entity.ExpenseType;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 은행/카드 명세서 CSV 텍스트를 (날짜, 금액, 수입/지출, 내용) 목록으로 바꿔주는 파서.
 *
 * 자동으로 처리하는 것
 *  1) 헤더 앞의 안내 줄 (예: "조회기간: ...")  --> 헤더를 찾을 때까지 건너뜀
 *  2) 구분자: 쉼표(,) / 탭 / 세미콜론(;) / 파이프(|)
 *  3) 형식 A: 출금액·입금액 컬럼이 따로 있음
 *     형식 B: 거래금액(금액) 컬럼 하나만 있음 (구분 컬럼이 있으면 그걸로, 없으면 부호로 판단)
 *  4) 읽지 못한 줄은 버리지 않고 "몇 번째 줄, 왜" 를 모아서 돌려줌
 */

public final class CsvStatementParser {

    private CsvStatementParser() {}

    /** 읽기에 성공한 한 줄 */
    public record Row(int line, LocalDate date, long amount, ExpenseType type, String desc) {}

    /** 파싱 결과: 성공한 줄들 + 건너뛴 줄들 */
    public record Result(List<Row> rows, List<SkippedRow> skipped) {}

    // ---- 은행/카드마다 다른 컬럼명 후보 (없는 이름이면 여기에 추가하면 됨) ----
    private static final List<String> DATE_COLS = List.of(
            "거래일시", "거래일자", "이용일자", "날짜", "거래일", "일자", "승인일자", "승인일시",
            "이용일", "거래날짜", "결제일", "date");
    private static final List<String> WITHDRAW_COLS = List.of(
            "출금액", "출금", "이용금액", "출금금액", "지출", "지출금액", "찾으신금액", "withdrawal");
    private static final List<String> DEPOSIT_COLS = List.of(
            "입금액", "입금", "입금금액", "수입", "수입금액", "맡기신금액", "deposit");
    private static final List<String> AMOUNT_COLS = List.of(          // 형식 B: 금액 하나
            "거래금액", "금액", "승인금액", "결제금액", "사용금액", "amount");
    private static final List<String> CARD_STYLE_AMOUNT_COLS = List.of( // 양수 = 지출인 카드 명세서 스타일
            "승인금액", "결제금액", "사용금액");
    private static final List<String> TYPE_COLS = List.of(            // 입금/출금 구분 컬럼
            "입출금구분", "거래구분", "입출구분", "구분", "거래유형", "유형", "type");
    private static final List<String> DESC_COLS = List.of(
            "적요", "내용", "가맹점명", "거래내용", "메모", "거래처", "상호", "상호명", "가맹점",
            "사용처", "거래기록사항", "거래기록", "비고");

    private static final char[] DELIMITERS = {',', '\t', ';', '|'};
    private static final int HEADER_SCAN_LIMIT = 50;   // 헤더를 찾을 때 위에서 몇 줄까지 볼지

    // ---------------------------------------------------------------
    // 메인: 텍스트 전체를 읽어 결과를 돌려준다. 헤더를 못 찾으면 IllegalArgumentException
    // ---------------------------------------------------------------
    public static Result parse(String text) {
        // -1 : 끝의 빈 줄도 유지해서 줄 번호가 실제 파일과 같게 함
        String[] lines = text.split("\\r\\n|\\n|\\r", -1);

        Header h = findHeader(lines);
        if (h == null) {
            // 날짜 컬럼은 있는데 금액 컬럼이 없는 경우와, 날짜 컬럼부터 없는 경우를 구분해서 안내
            if (anyLineHasColumn(lines, DATE_COLS)) {
                throw new IllegalArgumentException(
                        "금액 컬럼(출금액/입금액 또는 거래금액)을 찾을 수 없습니다. CSV 헤더를 확인해주세요.");
            }
            throw new IllegalArgumentException("날짜 컬럼을 찾을 수 없습니다. CSV 헤더를 확인해주세요.");
        }

        List<SkippedRow> skipped = new ArrayList<>();
        List<Row> rows = new ArrayList<>();

        // 형식 B는 "파일 전체에 음수가 있는지"를 봐야 부호 해석이 가능해서, 일단 모았다가 나중에 변환
        record Pending(int line, LocalDate date, long amount, String typeText, String desc) {}
        List<Pending> pendingB = new ArrayList<>();
        boolean anyNegative = false;

        for (int i = h.lineIdx + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;                      // 빈 줄은 조용히 무시
            int lineNo = i + 1;                                // 사람이 보는 줄 번호는 1부터
            List<String> c = splitLine(line, h.delim);

            // 1) 날짜
            String dateRaw = cell(c, h.date);
            LocalDate date = parseDate(dateRaw);
            if (date == null) {
                skipped.add(skip(lineNo, "날짜를 읽을 수 없음 (값: " + shorten(dateRaw) + ")", line));
                continue;
            }
            String desc = h.desc >= 0 ? cell(c, h.desc) : "";

            // 2-A) 형식 A: 출금액/입금액 컬럼
            if (h.withdraw >= 0 || h.deposit >= 0) {
                Long w = h.withdraw >= 0 ? parseAmount(cell(c, h.withdraw)) : Long.valueOf(0);
                Long d = h.deposit >= 0 ? parseAmount(cell(c, h.deposit)) : Long.valueOf(0);
                if (w == null || d == null) {
                    String bad = w == null ? cell(c, h.withdraw) : cell(c, h.deposit);
                    skipped.add(skip(lineNo, "금액을 숫자로 읽을 수 없음 (값: " + shorten(bad) + ")", line));
                    continue;
                }
                if (w < 0 || d < 0) {
                    skipped.add(skip(lineNo, "음수 금액 (취소/환불 등은 건너뜀)", line));
                    continue;
                }
                if (w == 0 && d == 0) {
                    skipped.add(skip(lineNo, "출금액·입금액이 모두 0이거나 비어 있음", line));
                    continue;
                }
                if (w > 0) rows.add(new Row(lineNo, date, w, ExpenseType.EXPENSE, desc));
                if (d > 0) rows.add(new Row(lineNo, date, d, ExpenseType.INCOME, desc));
                continue;
            }

            // 2-B) 형식 B: 금액 컬럼 하나
            String amountRaw = cell(c, h.amount);
            Long a = parseAmount(amountRaw);
            if (a == null) {
                skipped.add(skip(lineNo, "금액을 숫자로 읽을 수 없음 (값: " + shorten(amountRaw) + ")", line));
                continue;
            }
            if (a == 0) {
                skipped.add(skip(lineNo, "금액이 0이거나 비어 있음", line));
                continue;
            }
            if (a < 0) anyNegative = true;
            pendingB.add(new Pending(lineNo, date, a, h.type >= 0 ? cell(c, h.type) : "", desc));
        }

        // 형식 B 변환: 수입/지출 판단
        for (Pending p : pendingB) {
            ExpenseType type = parseType(p.typeText());                 // 1순위: 구분 컬럼("입금"/"출금")
            long abs = Math.abs(p.amount());
            if (type == null) {                                         // 2순위: 부호
                if (p.amount() < 0) {
                    type = ExpenseType.EXPENSE;                         // 음수 = 출금 (카드는 음수 = 취소/환불)
                    if (h.cardStyle) type = ExpenseType.INCOME;
                } else {
                    // 양수: 카드 스타일이면 지출. 은행 스타일은 파일에 음수가 있으면 "부호 있는 장부"라 입금,
                    // 음수가 하나도 없으면 전부 지출로 봄 (구분 정보가 없어 알 수 없으므로)
                    type = (h.cardStyle || !anyNegative) ? ExpenseType.EXPENSE : ExpenseType.INCOME;
                }
            }
            rows.add(new Row(p.line(), p.date(), abs, type, p.desc()));
        }
        rows.sort((x, y) -> Integer.compare(x.line(), y.line()));       // 파일 순서 유지
        return new Result(rows, skipped);
    }

    // ---------------------------------------------------------------
    // 헤더 찾기: 위에서부터 훑으며 "날짜 컬럼 + 금액 컬럼"이 있는 첫 줄을 헤더로 본다
    // ---------------------------------------------------------------
    private record Header(int lineIdx, char delim, int date, int withdraw, int deposit,
                          int amount, int type, int desc, boolean cardStyle, int cellCount) {}

    private static Header findHeader(String[] lines) {
        int limit = Math.min(lines.length, HEADER_SCAN_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (lines[i].isBlank()) continue;
            Header best = null;
            for (char d : DELIMITERS) {          // 구분자 후보를 모두 시도 --> 컬럼이 가장 많이 나뉘는 것 선택
                List<String> cells = splitLine(lines[i], d);
                int date = indexOf(cells, DATE_COLS);
                int w = indexOf(cells, WITHDRAW_COLS);
                int dep = indexOf(cells, DEPOSIT_COLS);
                int amt = indexOf(cells, AMOUNT_COLS);
                if (date < 0 || (w < 0 && dep < 0 && amt < 0)) continue;

                boolean card = amt >= 0 && CARD_STYLE_AMOUNT_COLS.contains(norm(cells.get(amt)));
                Header h = new Header(i, d, date, w, dep, amt, indexOf(cells, TYPE_COLS),
                        indexOf(cells, DESC_COLS), card, cells.size());
                if (best == null || h.cellCount > best.cellCount) best = h;
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean anyLineHasColumn(String[] lines, List<String> candidates) {
        int limit = Math.min(lines.length, HEADER_SCAN_LIMIT);
        for (int i = 0; i < limit; i++) {
            for (char d : DELIMITERS) {
                if (indexOf(splitLine(lines[i], d), candidates) >= 0) return true;
            }
        }
        return false;
    }

    // 후보 이름 순서대로, 실제 헤더 셀과 일치하는 첫 컬럼 번호 (없으면 -1)
    private static int indexOf(List<String> cells, List<String> candidates) {
        for (String cand : candidates) {
            for (int i = 0; i < cells.size(); i++) {
                if (norm(cells.get(i)).equals(cand)) return i;
            }
        }
        return -1;
    }

    // 헤더 비교용 정규화: BOM/공백/"(원)" 같은 괄호 단위 제거 + 소문자
    private static String norm(String s) {
        if (s == null) return "";
        return s.replace("\uFEFF", "")
                .replaceAll("\\(.*?\\)|\\[.*?]", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------
    // 한 줄을 구분자로 나눔. 큰따옴표로 감싼 값 안의 구분자(예: "1,234,000")는 나누지 않음
    // ---------------------------------------------------------------
    static List<String> splitLine(String line, char delim) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }  // "" --> "
                    else inQuotes = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == delim) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static String cell(List<String> cells, int idx) {
        return (idx >= 0 && idx < cells.size()) ? cells.get(idx) : "";
    }

    // ---------------------------------------------------------------
    // 금액: "1,234,000" "31400.0" "3.5E+6" "-5,000" "(5,000)" "5,000원" "₩5,000" 지원
    // 빈 값이면 0, 숫자로 읽을 수 없으면 null (--> "읽을 수 없음"으로 보고)
    // ---------------------------------------------------------------
    static Long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        String s = raw.replace(",", "").replace("원", "").replace("₩", "").replace("\\", "")
                .replaceAll("\\s+", "");
        if (s.isEmpty()) return 0L;
        boolean paren = s.startsWith("(") && s.endsWith(")");       // (5000) = 음수
        if (paren) s = "-" + s.substring(1, s.length() - 1);
        try {
            return new java.math.BigDecimal(s)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------
    // 날짜: "2026-09-05" "2026.09.05" "2026/9/5" "20260905" "2026-09-05 13:22:10"
    //       "2026년 9월 5일" "26.09.05" 등. 못 읽으면 null
    // ---------------------------------------------------------------
    private static final Pattern YMD = Pattern.compile("(\\d{4})\\D+(\\d{1,2})\\D+(\\d{1,2})");
    private static final Pattern COMPACT = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})(?:\\d{4}|\\d{6})?$");
    private static final Pattern YY = Pattern.compile("^(\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})(?!\\d)");

    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            Matcher m = COMPACT.matcher(s);
            if (m.find()) return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            m = YMD.matcher(s);
            if (m.find()) return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            m = YY.matcher(s);
            if (m.find()) return LocalDate.of(2000 + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (DateTimeException e) {
            return null;   // 13월, 32일 같은 잘못된 날짜
        }
        return null;
    }

    // "입금"/"출금" 같은 구분 값을 수입/지출로. 모르는 값이면 null (--> 부호로 판단)
    private static ExpenseType parseType(String raw) {
        String t = norm(raw);
        if (t.isEmpty()) return null;
        if (t.contains("출금") || t.contains("지출") || t.contains("expense") || t.contains("withdraw")
                || t.contains("debit") || t.contains("결제") || t.contains("승인") || t.contains("사용")) {
            return ExpenseType.EXPENSE;
        }
        if (t.contains("입금") || t.contains("수입") || t.contains("income") || t.contains("deposit")
                || t.contains("credit") || t.contains("급여")) {
            return ExpenseType.INCOME;
        }
        if (t.equals("-")) return ExpenseType.EXPENSE;
        if (t.equals("+")) return ExpenseType.INCOME;
        return null;
    }

    private static SkippedRow skip(int lineNo, String reason, String raw) {
        return new SkippedRow(lineNo, reason, shorten(raw, 120));
    }

    private static String shorten(String s) { return shorten(s, 30); }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}