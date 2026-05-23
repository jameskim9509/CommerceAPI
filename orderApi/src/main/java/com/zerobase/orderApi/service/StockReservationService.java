package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Order;
import com.zerobase.orderApi.domain.OrderItem;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.OrderRepository;
import com.zerobase.orderApi.repository.ProductItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-002 의 낙관적 락 충돌이 호출자(StockConsumer) 쪽에서 catch 가능하도록
 * REQUIRES_NEW 로 분리된 자체 트랜잭션에서 재고 차감 + Order 상태 전이를 수행한다.
 *  - 성공 시: Order 가 PENDING → PAID → CONFIRMED 로 일괄 커밋
 *  - 충돌 시: 트랜잭션 전체 롤백되어 Order 는 PENDING 으로 남고,
 *    ObjectOptimisticLockingFailureException 이 호출자에게 전파된다.
 */
@Service
@RequiredArgsConstructor
public class StockReservationService {

    private final OrderRepository orderRepository;
    private final ProductItemRepository productItemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveStock(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));

        order.markPaid();
        for (OrderItem item : order.getItems()) {
            ProductItem productItem = productItemRepository.findById(item.getProductItemId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_ITEM_NOT_FOUND));
            if (productItem.getCount() < item.getCount()) {
                throw new CustomException(ErrorCode.NOT_ENOUGH_ITEM_COUNT);
            }
            productItem.setCount(productItem.getCount() - item.getCount());
        }
        order.markConfirmed();
    }
}
