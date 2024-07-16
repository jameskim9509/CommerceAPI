package com.zerobase.userApi.service.customer;

import com.zerobase.userApi.domain.customer.CustomerBalanceHistory;
import com.zerobase.userApi.dto.ChangeBalanceDto;
import com.zerobase.userApi.exception.CustomException;
import com.zerobase.userApi.exception.ErrorCode;
import com.zerobase.userApi.repository.customer.CustomerBalanceHistoryRepository;
import com.zerobase.userApi.repository.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerBalanceHistoryService {
    private final CustomerBalanceHistoryRepository customerBalanceHistoryRepository;
    private final CustomerRepository customerRepository;

    // 오류에 대해 수행된 트랜잭션 기록
    @Transactional(noRollbackFor = {CustomException.class})
    public CustomerBalanceHistory changeBalance(
            Long customerId, ChangeBalanceDto.Input form
    ) throws CustomException
    {
         CustomerBalanceHistory customerBalanceHistory =
                 customerBalanceHistoryRepository.findByCustomerIdRecent(customerId)
                         .orElse(CustomerBalanceHistory.builder()
                                 .changeMoney(0)
                                 .currentMoney(0)
                                 .customer(
                                         customerRepository.findById(customerId)
                                                 .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND))
                                 ).build());

         if(customerBalanceHistory.getChangeMoney() + form.getMoney() < 0)
         {
             throw new CustomException(ErrorCode.NOT_ENOUGH_BALANCE);
         }

         customerBalanceHistory = CustomerBalanceHistory.builder()
                 .changeMoney(customerBalanceHistory.getChangeMoney() + form.getMoney())
                 .currentMoney(customerBalanceHistory.getChangeMoney())
                 .description(form.getMessage())
                 .fromMessage(form.getFrom())
                 .customer(customerBalanceHistory.getCustomer())
                 .build();

         customerBalanceHistoryRepository.save(customerBalanceHistory);
         customerBalanceHistory.getCustomer().setBalance(customerBalanceHistory.getChangeMoney());

         return customerBalanceHistory;
    }
}
