package com.zerobase.userApi.service.customer;

import com.zerobase.userApi.domain.customer.Customer;
import com.zerobase.userApi.dto.SendMailDto;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.dto.customer.CustomerVo;
import com.zerobase.userApi.exception.CustomException;
import com.zerobase.userApi.exception.ErrorCode;
import com.zerobase.userApi.repository.customer.CustomerRepository;
import com.zerobase.userApi.security.customer.CustomerDetails;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService implements UserDetailsService {

    private final CustomerRepository customerRepository;
    private final MailgunClient mailgunClient;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupDto.Output signUp(SignupDto.Input form)
    {
        if(isCustomerExistByEmail(form.getEmail()))
        {
            throw new CustomException(ErrorCode.ALREADY_EXIST_USER);
        }
        else {
            form.setPassword(
                    passwordEncoder.encode(form.getPassword())
            );
            Customer customer = customerRepository.save(form.toCustomerEntity());

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

            customer.changeVerificationInfo(
                    now.plusHours(1), code, false
            );

            return SignupDto.Output.builder()
                    .message("Verification Email이 발송되었습니다.")
                    .build();
        }
    }

    public boolean isCustomerExistByEmail(String email)
    {
        return customerRepository.findByEmail(email).isPresent();
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
        Customer customer = customerRepository.findByEmail(email).orElse(null);
        if(customer == null ||
                !customer.getVerificationCode().equals(code) ||
                    customer.getVerifyExpiredAt().isBefore(LocalDateTime.now()))
        {
            throw new CustomException(ErrorCode.VERIFICATION_FAILED);
        }

        customer.changeVerificationInfo(
                customer.getVerifyExpiredAt(), customer.getVerificationCode(), true
        );

        return SignupDto.Output.builder()
                .message("Verification에 성공하였습니다.")
                .build();
    }

    // Jwt
    @Override
    public UserDetails loadUserByUsername(String email)
    {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new CustomerDetails(customer);
    }

    public CustomerVo findValidCustomer(String email, String password)
    {
        Customer customer =
                customerRepository.findByEmail(email)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if(!passwordEncoder.matches(password, customer.getPassword()))
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        if(!customer.isVerify())
            throw new CustomException(ErrorCode.VERIFICATION_REQUIRED);

        else return CustomerVo.from(customer);
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
                    .append("http://ec2-43-201-50-243.ap-northeast-2.compute.amazonaws.com/user/customer/signup/verify?email=")
                    .append(email)
                    .append("&code=")
                    .append(code)
                    .toString();
    }


}
