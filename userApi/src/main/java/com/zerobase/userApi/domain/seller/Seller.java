package com.zerobase.userApi.domain.seller;

import com.zerobase.userApi.domain.BaseEntity;
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

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
@AuditOverride(forClass = BaseEntity.class)
public class Seller extends BaseEntity{
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

    private List<String> roles;

    public void changeVerificationInfo(
            LocalDateTime verifyExpiredAt, String verificationCode, boolean verify
    )
    {
        this.verifyExpiredAt = verifyExpiredAt;
        this.verificationCode = verificationCode;
        this.verify = verify;
    }
}
