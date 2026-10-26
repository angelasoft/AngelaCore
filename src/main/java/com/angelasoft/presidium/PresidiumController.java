package com.angelasoft.presidium;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRepository;
import com.angelasoft.presidium.PresidiumDtos.CuriaResponse;
import com.angelasoft.presidium.PresidiumDtos.CreatePresidiumRequest;
import com.angelasoft.presidium.PresidiumDtos.MyPresidiumResponse;
import com.angelasoft.presidium.PresidiumDtos.PresidiumResponse;
import com.angelasoft.region.Parish;
import com.angelasoft.region.ParishRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쁘레시디움(Pr) 조회·생성·가입 API.
 *
 *   GET  /app/presidiums?parishId=          본당 소속 Pr 목록
 *   GET  /app/presidiums/curias?parishId=   본당 소속 Cu 목록
 *   GET  /app/presidiums/me                 내 가입 Pr (인증 필요)
 *   POST /app/presidiums                    Pr 생성 (인증 필요)
 *   POST /app/presidiums/{id}/join          Pr 가입 (인증 필요)
 */
@RestController
@RequestMapping("/app/presidiums")
@RequiredArgsConstructor
public class PresidiumController {

    private final UserRepository userRepository;
    private final ParishRepository parishRepository;
    private final CuriaRepository curiaRepository;
    private final PresidiumRepository presidiumRepository;
    private final PresidiumMembershipRepository membershipRepository;

    @GetMapping
    public List<PresidiumResponse> listByParish(@RequestParam Long parishId) {
        return presidiumRepository.findByParishIdOrderByCuriaAndName(parishId).stream()
                .map(PresidiumResponse::from)
                .toList();
    }

    @GetMapping("/curias")
    public List<CuriaResponse> listCurias(@RequestParam Long parishId) {
        return curiaRepository.findByParishIdOrderByNameAsc(parishId).stream()
                .map(CuriaResponse::from)
                .toList();
    }

    @GetMapping("/me")
    public ResponseEntity<?> myPresidium(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        return membershipRepository.findByUserIdWithDetails(user.getId())
                .map(m -> ResponseEntity.ok(MyPresidiumResponse.of(m)))
                .orElseGet(() -> ResponseEntity.ok(MyPresidiumResponse.notJoined()));
    }

    @PostMapping("/{presidiumId}/join")
    @Transactional
    public ResponseEntity<?> join(
            @PathVariable Long presidiumId,
            Authentication authentication
    ) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }

        Presidium presidium = presidiumRepository.findByIdWithDetails(presidiumId).orElse(null);
        if (presidium == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "쁘레시디움을 찾을 수 없습니다."));
        }

        PresidiumMembership existing = membershipRepository.findByUserId(user.getId()).orElse(null);
        if (existing != null) {
            if (existing.getPresidium().getId().equals(presidiumId)) {
                return ResponseEntity.ok(MyPresidiumResponse.of(existing));
            }
            return alreadyJoined();
        }

        PresidiumMembership membership = membershipRepository.save(PresidiumMembership.builder()
                .user(user)
                .presidium(presidium)
                .build());
        return ResponseEntity.ok(MyPresidiumResponse.of(membership));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(
            @RequestBody CreatePresidiumRequest request,
            Authentication authentication
    ) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        if (membershipRepository.findByUserId(user.getId()).isPresent()) {
            return alreadyJoined();
        }

        if (request.parishId() == null) {
            return badRequest("parishId는 필수입니다.");
        }
        if (request.name() == null || request.name().isBlank()) {
            return badRequest("쁘레시디움 이름은 필수입니다.");
        }

        Parish parish = parishRepository.findById(request.parishId()).orElse(null);
        if (parish == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "본당을 찾을 수 없습니다."));
        }

        Curia curia = resolveCuria(parish, request);
        if (curia == null) {
            return badRequest("curiaId 또는 curiaName이 필요합니다.");
        }

        String presidiumName = request.name().trim();
        if (presidiumRepository.findByCuriaIdAndName(curia.getId(), presidiumName).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "같은 큐리아에 동일한 이름의 쁘레시디움이 이미 있습니다."));
        }

        int session = request.sessionNumber() == null || request.sessionNumber() < 1
                ? 1
                : request.sessionNumber();

        Presidium saved = presidiumRepository.save(Presidium.builder()
                .curia(curia)
                .name(presidiumName)
                .sessionNumber(session)
                .createdBy(user)
                .build());

        // 만든 사람이 첫 단장으로 가입한다.
        PresidiumMembership founder = membershipRepository.save(PresidiumMembership.builder()
                .user(user)
                .presidium(saved)
                .officerRole(OfficerRole.PRESIDENT)
                .build());

        return ResponseEntity.status(HttpStatus.CREATED).body(MyPresidiumResponse.of(founder));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AdminUserDetails principal)) {
            return null;
        }
        return userRepository.findById(principal.getUserId()).orElse(null);
    }

    private Curia resolveCuria(Parish parish, CreatePresidiumRequest request) {
        if (request.curiaId() != null) {
            return curiaRepository.findById(request.curiaId())
                    .filter(c -> c.getParish().getId().equals(parish.getId()))
                    .orElse(null);
        }
        if (request.curiaName() == null || request.curiaName().isBlank()) {
            return null;
        }
        String curiaName = request.curiaName().trim();
        return curiaRepository.findByParishIdAndName(parish.getId(), curiaName)
                .orElseGet(() -> curiaRepository.save(
                        Curia.builder().parish(parish).name(curiaName).build()));
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private ResponseEntity<Map<String, String>> alreadyJoined() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", "이미 가입한 쁘레시디움이 있습니다. 퇴단 승인 후 다른 쁘레시디움에 가입할 수 있습니다."));
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "UNAUTHORIZED"));
    }
}
