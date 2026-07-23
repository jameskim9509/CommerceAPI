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

// =============================================================================
// [ADR-008 control 무방어 오버레이] 원하는 장애 ④ 잔액 부족→결제 실패 방어(잔액 검증) 제거.
//   NOT_ENOUGH_BALANCE 검사를 없애 음수 잔액을 허용 → PaymentFailed 분기가 사라지고 잔액이 음수로 붕괴.
//   빌드 중에만 원본 위에 덮어씀. 원본과 NOT_ENOUGH_BALANCE 검사 유무만 다르다: userApi/.../service/customer/CustomerBalanceHistoryService.java
// =============================================================================
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

         // (control) ④ 잔액 부족 검증(NOT_ENOUGH_BALANCE) 제거 — 음수 잔액 허용.

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
