package com.angelasoft.presidium;

public final class PresidiumDtos {

    private PresidiumDtos() {
    }

    public record PresidiumResponse(
            Long id,
            String curiaName,
            Long curiaId,
            String name,
            int sessionNumber,
            Long parishId,
            String parishName
    ) {
        public static PresidiumResponse from(Presidium p) {
            return new PresidiumResponse(
                    p.getId(),
                    p.getCuria().getName(),
                    p.getCuria().getId(),
                    p.getName(),
                    p.getSessionNumber(),
                    p.getCuria().getParish().getId(),
                    p.getCuria().getParish().getName()
            );
        }
    }

    public record CuriaResponse(Long id, String name) {
        public static CuriaResponse from(Curia c) {
            return new CuriaResponse(c.getId(), c.getName());
        }
    }

    public record CreatePresidiumRequest(
            Long parishId,
            Long curiaId,
            String curiaName,
            String name,
            Integer sessionNumber
    ) {
    }

    /** 내 가입 정보. grade·officerRole 은 앱에서 메뉴 노출(간부 전용 등)을 정하는 데 쓴다. */
    public record MyPresidiumResponse(
            boolean joined,
            PresidiumResponse presidium,
            MemberGrade grade,
            String gradeLabel,
            OfficerRole officerRole,
            String officerLabel
    ) {
        public static MyPresidiumResponse notJoined() {
            return new MyPresidiumResponse(false, null, null, null, null, null);
        }

        public static MyPresidiumResponse of(PresidiumMembership m) {
            OfficerRole role = m.getOfficerRole();
            return new MyPresidiumResponse(
                    true,
                    PresidiumResponse.from(m.getPresidium()),
                    m.getGrade(),
                    m.getGrade().getLabel(),
                    role,
                    role == null ? null : role.getLabel());
        }
    }
}
