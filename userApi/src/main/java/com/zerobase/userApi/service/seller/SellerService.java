package com.zerobase.userApi.service.seller;

import com.zerobase.userApi.domain.seller.Seller;
import com.zerobase.userApi.dto.SendMailDto;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.exception.CustomException;
import com.zerobase.userApi.exception.ErrorCode;
import com.zerobase.userApi.repository.seller.SellerRepository;
import com.zerobase.userApi.security.seller.SellerDetails;
import com.zerobase.userApi.service.MailgunClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Service
public class SellerService implements UserDetailsService {

    private final SellerRepository sellerRepository;
    private final MailgunClient mailgunClient;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupDto.Output signUp(SignupDto.Input form)
    {
        if(isSellerExistByEmail(form.getEmail()))
        {
            throw new CustomException(ErrorCode.ALREADY_EXIST_USER);
        }
        else {
            form.setPassword(
                    passwordEncoder.encode(form.getPassword())
            );
            Seller seller = sellerRepository.save(form.toSellerEntity());

            LocalDateTime now = LocalDateTime.now();
            String code = getRandomCode();

            sendEmail(
                    SendMailDto.builder()
                            .from("commerceAPI@application.com")
                            .to(form.getEmail())
                            .subject("Commerce Validation Email")
                            .text(getVerificationEmailBody(
                                    form.getEmail(), form.getName(), code)
                            )
                            .build()
            );

            seller.changeVerificationInfo(
                    now.plusHours(1), code, false
            );

            return SignupDto.Output.builder()
                    .message("Verification Email이 발송되었습니다.")
                    .build();
        }
    }

    public boolean isSellerExistByEmail(String email)
    {
        return sellerRepository.findByEmail(email).isPresent();
    }

    public ResponseEntity sendEmail(SendMailDto form)
    {
        ResponseEntity response = mailgunClient.sendEmail(form);
        if(response.getStatusCode() != HttpStatusCode.valueOf(200))
        {
            log.error("statusCode: {}, Body: {}", response.getStatusCode(), response.getBody());
            throw new CustomException(ErrorCode.SEND_EMAIL_ERROR);
        }
        else return response;
    }

    @Transactional
    public SignupDto.Output verfiyEmail(String email, String code)
    {
        Seller seller = sellerRepository.findByEmail(email).orElse(null);
        if(seller == null ||
                !seller.getVerificationCode().equals(code) ||
                seller.getVerifyExpiredAt().isBefore(LocalDateTime.now()))
        {
            throw new CustomException(ErrorCode.VERIFICATION_FAILED);
        }

        seller.changeVerificationInfo(
                seller.getVerifyExpiredAt(), seller.getVerificationCode(), true
        );

        return SignupDto.Output.builder()
                .message("Verification에 성공하였습니다.")
                .build();
    }

    // Jwt
    @Override
    public UserDetails loadUserByUsername(String email)
    {
        Seller seller = sellerRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new SellerDetails(seller);
    }

    public Seller findValidSeller(String email, String password)
    {
        Seller seller =
                sellerRepository.findByEmail(email)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if(!passwordEncoder.matches(password, seller.getPassword()))
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        if(!seller.isVerify())
            throw new CustomException(ErrorCode.VERIFICATION_REQUIRED);

        else return seller;
    }

    private String getRandomCode()
    {
        return RandomStringUtils.random(10, true, true);
    }

    private String getVerificationEmailBody(String email, String name, String code)
    {
        StringBuilder sb = new StringBuilder();
        return sb.append("Dear ").append(name).append("\n")
                .append("verification 링크를 클릭해주세요.\n\n")
                .append("http://localhost:8081/seller/signup/verify?email=")
                .append(email)
                .append("&code=")
                .append(code)
                .toString();
    }

}
