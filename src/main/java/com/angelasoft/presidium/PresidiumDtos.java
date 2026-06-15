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

    public record MyPresidiumResponse(boolean joined, PresidiumResponse presidium) {
        public static MyPresidiumResponse notJoined() {
            return new MyPresidiumResponse(false, null);
        }

        public static MyPresidiumResponse of(Presidium p) {
            return new MyPresidiumResponse(true, PresidiumResponse.from(p));
        }
    }
}
