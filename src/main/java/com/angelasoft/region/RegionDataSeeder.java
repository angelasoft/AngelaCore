package com.angelasoft.region;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 시작 시 클래스패스의 교구/지구/본당 CSV를 읽어 DB에 시드한다.
 * 이미 데이터가 있으면(본당 존재) 건너뛴다.
 *
 * CSV: data/parishes.csv
 * 컬럼: diocese_code, diocese_name, district, parish_name, parish_code, parish_type
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionDataSeeder implements ApplicationRunner {

    private static final String CSV_PATH = "data/parishes.csv";

    private final DioceseRepository dioceseRepository;
    private final DistrictRepository districtRepository;
    private final ParishRepository parishRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (parishRepository.count() > 0) {
            log.info("[RegionSeeder] 본당 데이터 이미 존재({}건) → 시드 건너뜀", parishRepository.count());
            return;
        }

        List<String[]> rows = readCsv();
        if (rows.isEmpty()) {
            log.warn("[RegionSeeder] CSV가 비어 있어 시드하지 않음: {}", CSV_PATH);
            return;
        }

        Map<String, Diocese> dioceseByCode = new HashMap<>();
        Map<String, District> districtByKey = new HashMap<>();
        int parishCount = 0;

        for (String[] cols : rows) {
            if (cols.length < 6) {
                continue;
            }
            String dioceseCode = cols[0].trim();
            String dioceseName = cols[1].trim();
            String districtName = cols[2].trim();
            String parishName = cols[3].trim();
            String parishCode = cols[4].trim();
            String parishType = cols[5].trim();

            if (parishCode.isEmpty()) {
                continue;
            }

            Diocese diocese = dioceseByCode.computeIfAbsent(dioceseCode, code ->
                    dioceseRepository.save(Diocese.builder().code(code).name(dioceseName).build()));

            String districtKey = dioceseCode + "|" + districtName;
            District district = districtByKey.computeIfAbsent(districtKey, key ->
                    districtRepository.save(District.builder().diocese(diocese).name(districtName).build()));

            parishRepository.save(Parish.builder()
                    .district(district)
                    .code(parishCode)
                    .name(parishName)
                    .type(parishType.isEmpty() ? "본당" : parishType)
                    .build());
            parishCount++;
        }

        log.info("[RegionSeeder] 시드 완료 - 교구 {}, 지구 {}, 본당 {}",
                dioceseByCode.size(), districtByKey.size(), parishCount);
    }

    private List<String[]> readCsv() throws IOException {
        List<String[]> rows = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(CSV_PATH);
        try (InputStream in = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    line = stripBom(line);
                    continue; // 헤더 스킵
                }
                if (line.isBlank()) {
                    continue;
                }
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    private static String stripBom(String s) {
        return (!s.isEmpty() && s.charAt(0) == '\uFEFF') ? s.substring(1) : s;
    }

    /** 따옴표로 감싼 필드(내부 콤마 포함)를 처리하는 최소 CSV 파서. */
    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
