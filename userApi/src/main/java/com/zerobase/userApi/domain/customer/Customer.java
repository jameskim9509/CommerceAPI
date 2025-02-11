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
