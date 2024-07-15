package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.dto.ChangeBalanceDto;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.ProductItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final RedisClientService redisClientService;
    private final ProductItemRepository productItemRepository;
    private final CartService cartService;
    private final UserClient userClient;

    @Transactional
    public String order(String bearerToken, Long customerId, String username, Cart orderCart)
    {
        // 장바구니 확인
        Cart curCart =  redisClientService.get(customerId, Cart.class);
        // 롤백 상황에 대비
        Cart cloneCurCart = curCart.clone();
        if(curCart == null)
            throw new CustomException(ErrorCode.CART_NOT_EXIST);
        try
        {
            // 현재 장바구니에 대한 검증
            cartService.refreshCart(curCart, customerId);
            // 주문할 장바구니에 대한 검증
            cartService.refreshCart(orderCart, customerId);

            if(curCart.getMessages().size() > 0)
                throw new CustomException(ErrorCode.CART_CHECK_REQUIRED);
            if(orderCart.getMessages().size() > 0)
                throw new CustomException(ErrorCode.NOT_VALID_ORDER);

            Map<Long, Cart.ProductItem> curCartProductItemMap =
                    curCart.getProductList().stream()
                            .flatMap(p -> p.getProductItemList().stream())
                                    .collect(Collectors.toMap(Cart.ProductItem::getId, it -> it));

            // 기존 장바구니에서 주문할 아이템의 수량을 빼준다.
            orderCart.getProductList().stream()
                    .flatMap(p -> p.getProductItemList().stream())
                    .forEach(
                            orderIt -> {
                                Cart.ProductItem curIt = curCartProductItemMap.get(orderIt.getId());
                                if(curIt == null)
                                    throw new CustomException(ErrorCode.NOT_VALID_ORDER);
                                else
                                {
                                    if(curIt.getCount() < orderIt.getCount())
                                        throw new CustomException(ErrorCode.NOT_VALID_ORDER);

                                    curIt.setCount(curIt.getCount() - orderIt.getCount());
                                }
                            }
                    );

            // 수량이 0이 된 장바구니 아이템 제거
            Iterator<Cart.Product> productIterator = curCart.getProductList().iterator();
            while(productIterator.hasNext())
            {
                Iterator<Cart.ProductItem> productItemIterator =
                        productIterator.next().getProductItemList().iterator();
                while(productItemIterator.hasNext())
                {
                    Cart.ProductItem it = productItemIterator.next();
                    if(it.getCount().equals(0)) productItemIterator.remove();
                }
            }

            // 총 결제 금액
            int totalPrice = orderCart.getProductList().stream()
                    .flatMap(p -> p.getProductItemList().stream())
                            .mapToInt(it -> it.getCount() * it.getPrice())
                                    .sum();

            // 결제 시도
            HttpStatusCode httpStatusCode =
                userClient.changeBalance(
                        bearerToken, ChangeBalanceDto.Input.builder()
                                        .from(username)
                                        .message("상품 주문")
                                        .money(-totalPrice)
                                        .build()
                ).getStatusCode();

            // 결제 시도에 실패할 경우
            if(httpStatusCode.is4xxClientError())
                throw new CustomException(ErrorCode.PAYMENT_ERROR);

            // 재고 변경
            orderCart.getProductList().stream()
                    .flatMap(p -> p.getProductItemList().stream())
                    .forEach(it -> {
                                ProductItem item = productItemRepository.findById(it.getId()).get();
                                item.setCount(item.getCount() - it.getCount());
                            }
                    );

            redisClientService.put(customerId, curCart);

            return "주문이 완료되었습니다.";
        }
        catch (CustomException e)
        {
            // 변경 취소
            redisClientService.put(customerId, cloneCurCart);
            throw e;
        }
    }
}
