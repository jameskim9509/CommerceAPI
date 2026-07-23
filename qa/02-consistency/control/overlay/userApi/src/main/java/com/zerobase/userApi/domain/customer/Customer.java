package com.zerobase.userApi.domain.customer;

import com.zerobase.userApi.domain.BaseEntity;
import com.zerobase.userApi.security.Authority;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// =============================================================================
// [ADR-008 control 무방어 오버레이] 원하는 장애 ⑤ 잔액 동시성 Lost Update 방어(잔액 낙관적 락) 제거: @Version 삭제.
//   → 같은 고객 동시 결제/환불이 서로를 덮어써 잔액이 유실/창조된다(돈 보존 파괴).
//   customer.version 컬럼은 DB(NOT NULL DEFAULT 0)에 남지만 엔티티가 매핑 안 함 → ddl-auto:validate OK.
//   빌드 중에만 원본 위에 덮어씀. 원본과 @Version 유무만 다르다: userApi/.../domain/customer/Customer.java
// =============================================================================
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
@AuditOverride(forClass = BaseEntity.class)
public class Customer extends BaseEntity{

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String name;
    private String password;
    private LocalDate birth;
    private String phoneNum;

    private LocalDateTime verifyExpiredAt;
    private String verificationCode;
    private boolean verify = false;

    @Column(columnDefinition = "int default 0")
    private Integer balance;

    // (control) ADR-002 잔액 낙관적 락(@Version) 제거 — 동시 결제/환불 Lost Update 방어 없음.

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles;

    public void changeVerificationInfo(
            LocalDateTime verifyExpiredAt, String verificationCode, boolean verify
    )
    {
        this.verifyExpiredAt = verifyExpiredAt;
        this.verificationCode = verificationCode;
        this.verify = verify;
    }

    public void setBalance(Integer balance)
    {
        this.balance = balance;
    }
}
