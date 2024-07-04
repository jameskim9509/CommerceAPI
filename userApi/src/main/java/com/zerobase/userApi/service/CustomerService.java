package com.zerobase.userApi.service;

import com.zerobase.userApi.domain.Customer;
import com.zerobase.userApi.dto.CustomerDto;
import com.zerobase.userApi.dto.SendMailDto;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.repository.CustomerRepository;
import feign.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final MailgunClient mailgunClient;

    CustomerService(@Autowired CustomerRepository customerRepository,
                    @Autowired MailgunClient mailgunClient)
    {
        this.customerRepository = customerRepository;
        this.mailgunClient = mailgunClient;
    }

    public SignupDto.Output signUp(SignupDto.Input form)
    {
        Customer customer = customerRepository.save(form.toCustomerEntity());

        return SignupDto.Output.builder()
                .name(customer.getName())
                .build();
    }

    public CustomerDto getCustomer(String name)
    {
        Customer customer =
                customerRepository.findByName(name).orElse(null);

        return CustomerDto.from(customer);
    }

    public ResponseEntity sendEmail(SendMailDto form)
    {
        return mailgunClient.sendEmail(form);
    }
}
