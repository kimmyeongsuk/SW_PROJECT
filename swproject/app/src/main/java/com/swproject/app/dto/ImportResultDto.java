package com.swproject.app.dto;

import java.util.List;

/** CSV 업로드 결과: 등록/중복/건너뜀 건수와 건너뛴 줄의 이유 */
public record ImportResultDto(
        int saved,                    // 새로 등록된 건수
        int duplicates,               // 이미 있어서 제외한 중복 건수
        int skippedCount,             // 읽지 못해 건너뛴 줄 수 (중복 제외)
        List<SkippedRow> skippedRows  // 건너뛴 줄 상세 (화면용으로 최대 100줄)
) {
    /** 건너뛴 줄 하나 */
    public record SkippedRow(
            int lineNumber,   // 파일 안에서의 줄 번호 (1부터)
            String reason,    // 건너뛴 이유
            String raw        // 원래 줄 내용 (너무 길면 잘라냄)
    ) {
    }
}