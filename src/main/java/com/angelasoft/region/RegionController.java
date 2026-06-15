package com.angelasoft.region;

import com.angelasoft.region.RegionDtos.DioceseResponse;
import com.angelasoft.region.RegionDtos.DistrictResponse;
import com.angelasoft.region.RegionDtos.ParishResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 교구/지구/본당 조회 API (앱 쁘레시디움 가입 등에서 사용).
 *
 *   GET /app/regions/dioceses
 *   GET /app/regions/dioceses/{dioceseId}/districts
 *   GET /app/regions/districts/{districtId}/parishes
 *   GET /app/regions/dioceses/{dioceseId}/parishes
 *   GET /app/regions/parishes/search?query=
 */
@RestController
@RequestMapping("/app/regions")
@RequiredArgsConstructor
public class RegionController {

    private final DioceseRepository dioceseRepository;
    private final DistrictRepository districtRepository;
    private final ParishRepository parishRepository;

    @GetMapping("/dioceses")
    public List<DioceseResponse> dioceses() {
        return dioceseRepository.findAllByOrderByIdAsc().stream()
                .map(DioceseResponse::from)
                .toList();
    }

    @GetMapping("/dioceses/{dioceseId}/districts")
    public List<DistrictResponse> districts(@PathVariable Long dioceseId) {
        return districtRepository.findByDioceseIdOrderByIdAsc(dioceseId).stream()
                .map(DistrictResponse::from)
                .toList();
    }

    @GetMapping("/districts/{districtId}/parishes")
    public List<ParishResponse> parishesByDistrict(@PathVariable Long districtId) {
        return parishRepository.findByDistrictIdOrderByNameAsc(districtId).stream()
                .map(ParishResponse::from)
                .toList();
    }

    @GetMapping("/dioceses/{dioceseId}/parishes")
    public List<ParishResponse> parishesByDiocese(@PathVariable Long dioceseId) {
        return parishRepository.findByDistrictDioceseIdOrderByNameAsc(dioceseId).stream()
                .map(ParishResponse::from)
                .toList();
    }

    @GetMapping("/parishes/search")
    public List<ParishResponse> searchParishes(@RequestParam("query") String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return parishRepository.findTop30ByNameContainingOrderByNameAsc(q).stream()
                .map(ParishResponse::from)
                .toList();
    }
}
