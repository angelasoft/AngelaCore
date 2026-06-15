package com.angelasoft.region;

/** 지역(교구/지구/본당) 조회 응답 DTO 모음. */
public final class RegionDtos {

    private RegionDtos() {
    }

    public record DioceseResponse(Long id, String code, String name) {
        public static DioceseResponse from(Diocese d) {
            return new DioceseResponse(d.getId(), d.getCode(), d.getName());
        }
    }

    public record DistrictResponse(Long id, String name) {
        public static DistrictResponse from(District d) {
            return new DistrictResponse(d.getId(), d.getName());
        }
    }

    public record ParishResponse(Long id, String name, String type, Long districtId, String districtName) {
        public static ParishResponse from(Parish p) {
            return new ParishResponse(
                    p.getId(),
                    p.getName(),
                    p.getType(),
                    p.getDistrict().getId(),
                    p.getDistrict().getName()
            );
        }
    }
}
