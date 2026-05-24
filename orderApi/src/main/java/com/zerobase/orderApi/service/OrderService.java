package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.domain.Order;
import com.zerobase.orderApi.domain.OrderItem;
import com.zerobase.orderApi.domain.OrderStatus;
import com.zerobase.orderApi.dto.OrderDto;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.OrderRepository;
import com.zerobase.orderApi.saga.SagaEventPublisher;
import com.zerobase.orderApi.saga.SagaTopics;
import com.zerobase.orderApi.saga.event.SagaEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ADR-003: Choreography SAGA 의 진입점.
 *  - 장바구니 검증 후 Order(PENDING) 을 영속화하고 OrderCreated 이벤트를 발행한다.
 *  - 결제(userApi) / 재고 차감 / 보상은 모두 컨슈머가 처리하므로 동기 호출은 없다.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RedisClientService redisClientService;
    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final SagaEventPublisher publisher;

    @Transactional
    public OrderDto order(Long customerId, String username, Cart orderCart) {
        Cart curCart = redisClientService.get(customerId, Cart.class);
        if (curCart == null) throw new CustomException(ErrorCode.CART_NOT_EXIST);
        Cart cloneCurCart = curCart.clone();

        try {
            cartService.refreshCart(curCart, customerId);
            cartService.refreshCart(orderCart, customerId);

            if (!curCart.getMessages().isEmpty()) throw new CustomException(ErrorCode.CART_CHECK_REQUIRED);
            if (!orderCart.getMessages().isEmpty()) throw new CustomException(ErrorCode.NOT_VALID_ORDER);

            decrementCurCart(curCart, orderCart);
            removeZeroCountItems(curCart);

            int totalPrice = totalPrice(orderCart);

            Order order = Order.builder()
                    .customerId(customerId)
                    .username(username)
                    .status(OrderStatus.PENDING)
                    .totalPrice(totalPrice)
                    .items(buildOrderItems(orderCart))
                    .build();
            orderRepository.save(order);

            redisClientService.put(customerId, curCart);

            publisher.publish(SagaTopics.ORDER_CREATED, SagaEvents.OrderCreated.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(order.getId())
                    .customerId(customerId)
                    .username(username)
                    .totalPrice(totalPrice)
                    .build());

            return OrderDto.from(order);
        } catch (CustomException e) {
            redisClientService.put(customerId, cloneCurCart);
            throw e;
        }
    }

    private void decrementCurCart(Cart curCart, Cart orderCart) {
        Map<Long, Cart.ProductItem> curMap = curCart.getProductList().stream()
                .flatMap(p -> p.getProductItemList().stream())
                .collect(Collectors.toMap(Cart.ProductItem::getId, it -> it));

        orderCart.getProductList().stream()
                .flatMap(p -> p.getProductItemList().stream())
                .forEach(orderIt -> {
                    Cart.ProductItem curIt = curMap.get(orderIt.getId());
                    if (curIt == null) throw new CustomException(ErrorCode.NOT_VALID_ORDER);
                    if (curIt.getCount() < orderIt.getCount()) throw new CustomException(ErrorCode.NOT_VALID_ORDER);
                    curIt.setCount(curIt.getCount() - orderIt.getCount());
                });
    }

    private void removeZeroCountItems(Cart curCart) {
        Iterator<Cart.Product> productIt = curCart.getProductList().iterator();
        while (productIt.hasNext()) {
            Iterator<Cart.ProductItem> itemIt = productIt.next().getProductItemList().iterator();
            while (itemIt.hasNext()) {
                if (itemIt.next().getCount().equals(0)) itemIt.remove();
            }
        }
    }

    private int totalPrice(Cart orderCart) {
        return orderCart.getProductList().stream()
                .flatMap(p -> p.getProductItemList().stream())
                .mapToInt(it -> it.getCount() * it.getPrice())
                .sum();
    }

    private List<OrderItem> buildOrderItems(Cart orderCart) {
        return orderCart.getProductList().stream()
                .flatMap(p -> p.getProductItemList().stream())
                .map(it -> OrderItem.builder()
                        .productItemId(it.getId())
                        .name(it.getName())
                        .count(it.getCount())
                        .price(it.getPrice())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(Long customerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getCustomerId().equals(customerId)) {
            throw new CustomException(ErrorCode.ORDER_NOT_FOUND);
        }
        return OrderDto.from(order);
    }
}
