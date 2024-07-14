package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.dto.AddProductCartForm;
import com.zerobase.orderApi.dto.ProductItemDto;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CartService {

    private final RedisClientService redisClientService;
    private final ProductRepository productRepository;

    public Cart addCart(Long customerId, AddProductCartForm form)
    {
        Cart cart = redisClientService.get(customerId, Cart.class);

        validateForm(cart, form);

        if(cart == null)
        {
            cart = new Cart();
            cart.setCustomerId(customerId);
            cart.setProductList(new ArrayList<>());
            cart.setMessages(new ArrayList<>());
        }

        Optional<Cart.Product> productOptional =
                cart.getProductList().stream()
                .filter(product -> product.getId().equals(form.getId()))
                .findFirst();

        if(productOptional.isPresent())
        {
            Cart.Product redisProduct = productOptional.get();

            List<Cart.ProductItem> productItemList =
                    form.getProductItemList().stream()
                            .map(ProductItemDto::toProductItemRedisEntity)
                            .collect(Collectors.toList());

            // productItem ID로 장바구니에 있는 productItem 객체를 가져오기 위함
            Map<Long, Cart.ProductItem> redisItemMap =
                    redisProduct.getProductItemList().stream()
                            .collect(Collectors.toMap(Cart.ProductItem::getId, it -> it));


            // 이름이 일치하지 않을 때
            if(!redisProduct.getName().equals(form.getName()))
            {
                cart.addMessage("상품 이름이 일치하지 않습니다. 확인 요망");
            }
            for(Cart.ProductItem item : productItemList)
            {
                Cart.ProductItem redisItem =
                        redisItemMap.getOrDefault(item.getId(), null);

                if(redisItem == null)
                {
                    redisProduct.getProductItemList().add(item);
                }
                else
                {
                    // 가격이 일치하지 않을 때
                    if(redisItem.getPrice() != item.getPrice())
                    {
                        cart.addMessage(
                                redisItem.getName() + "의 가격이 일치하지 않습니다. 확인 요망"
                        );
                        redisItem.setCount(redisItem.getCount() + item.getCount());
                    }
                }
            }
        }
        else
        {
            Cart.Product product = form.toProductRedisEntity();
            cart.getProductList().add(product);
        }

        redisClientService.put(customerId, cart);
        return cart;
    }

    private void validateForm(Cart cart, AddProductCartForm form)
    {
        Product product = productRepository.findById(form.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        // 상품 아이템의 수량 검증
        if(cart != null)
        {
            // 장바구니에 존재하는 상품 확인
            Cart.Product cartProduct = cart.getProductList().stream()
                    .filter(p -> p.getId().equals(form.getId()))
                    .findFirst()
                    .orElse(null);

            // seller가 등록한 item count를 얻어오는 map 생성
            Map<Long, Integer> currentItemCntMap = product.getProductItemList().stream()
                    .collect(Collectors.toMap(ProductItem::getId, ProductItem::getCount));

            // 장바구니에 존재하는 상품의 item count를 얻어오는 map 생성
            Map<Long, Integer> cartItemCntMap =
                    cartProduct == null ? null : cartProduct.getProductItemList().stream()
                    .collect(Collectors.toMap(Cart.ProductItem::getId, Cart.ProductItem::getCount));

            // 수량 검증
            form.getProductItemList().stream().forEach(
                    formItem -> {
                        Integer cartItemCnt = cartItemCntMap == null ?
                                0 : cartItemCntMap.getOrDefault(formItem.getId(), 0);
                        Integer currentItemCnt = currentItemCntMap.get(formItem.getId());

                        if(cartItemCnt + formItem.getCount() > currentItemCnt)
                            throw new CustomException(ErrorCode.NOT_ENOUGH_ITEM_COUNT);
                    }
            );
        }
    }
}
