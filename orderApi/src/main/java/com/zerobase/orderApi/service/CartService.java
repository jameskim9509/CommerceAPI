package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.dto.AddProductCartForm;
import com.zerobase.orderApi.dto.ProductItemDto;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.ProductItemRepository;
import com.zerobase.orderApi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CartService {

    private final RedisClientService redisClientService;
    private final ProductRepository productRepository;
    private final ProductItemRepository productItemRepository;

    public Cart addCart(Long customerId, AddProductCartForm form)
    {
        Cart cart = redisClientService.get(customerId, Cart.class);

        validateAddForm(cart, form);

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
                    if(redisItem.getPrice().intValue() != item.getPrice().intValue())
                    {
                        cart.addMessage(
                                redisItem.getName() + "의 가격이 일치하지 않습니다. 확인 요망"
                        );
                    }
                    redisItem.setCount(redisItem.getCount() + item.getCount());
                }
            }
        }
        else
        {
            Cart.Product product = form.toProductRedisEntity();
            cart.getProductList().add(product);
        }

        // 상품을 계속 추가할 수 있기 때문에 확인이 필요한 메시지를 지우지 않음
        redisClientService.put(customerId, cart);
        return cart;
    }

    private void validateAddForm(Cart cart, AddProductCartForm form)
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

    public Cart getCart(Long customerId)
    {
        Cart cart = redisClientService.get(customerId, Cart.class);
        if(cart != null)
        {
            refreshCart(cart, customerId);

            Cart redisCart = cart.clone();
            redisCart.setMessages(new ArrayList<>());
            redisClientService.put(customerId, redisCart); // 해결했기 때문에 저장시에는 메시지 지우기

            return cart;
        }
        else {
            Cart newCart = Cart.builder()
                .customerId(customerId)
                .productList(new ArrayList<>())
                .messages(new ArrayList<>())
                .build();

            newCart.addMessage("조회할 장바구니가 비어있습니다.");
            return newCart;
        }
    }

    public void refreshCart(Cart cart, Long customerId)
    {
        // 장바구니에 있는 productList를 가져오고, 각각에 해당되는 DB 엔티티와 비교하기
        Iterator<Cart.Product> cartProductIterator = cart.getProductList().iterator();
        while(cartProductIterator.hasNext())
        {
            Cart.Product cartProduct = cartProductIterator.next();
            Product product = productRepository.findById(cartProduct.getId())
                    .orElse(null);

            // 등록된 상품이 제거된 상품일 경우
            if(product == null)
            {
                cart.addMessage("상품" + cartProduct.getId() + "번이 제거되었습니다.");
                cartProductIterator.remove();
                continue;
            }

            // 등록된 product와 장바구니의 product 내용 비교
            if (!product.getName().equals(cartProduct.getName()))
            {
                cart.addMessage("상품명"+ cartProduct.getName() + "이 " + product.getName() + "으로 변경되었습니다.");
                cartProduct.setName(product.getName());
            }
            if (!product.getDescription().equals(cartProduct.getDescription()))
            {
                cart.addMessage("상품설명"+ cartProduct.getDescription() + "이 " + product.getDescription() + "으로 변경되었습니다.");
                cartProduct.setDescription(product.getDescription());
            }

            Iterator<Cart.ProductItem> cartProductItemIterator = cartProduct.getProductItemList().iterator();
            while(cartProductItemIterator.hasNext())
            {
                Cart.ProductItem cartItem = cartProductItemIterator.next();

                ProductItem item = productItemRepository.findById(cartItem.getId())
                        .orElse(null);

                // 장바구니 아이템이 제거된 아이템일 경우
                if(item == null)
                {
                    cart.addMessage("상품" + cartProduct.getId() + "내 " + cartItem.getId() + "번 아이템이 제거되었습니다.");
                    cartProductItemIterator.remove();
                    continue;
                }

                // 등록된 productItem와 장바구니의 productItem 내용 비교
                if(!item.getName().equals(cartItem.getName()))
                {
                    cart.addMessage(
                            cartProduct.getId() + "번의 상품 내 상품 아이템 명 " + cartItem.getName() + "이 " + item.getName() + "으로 변경되었습니다."
                    );
                    cartItem.setName(item.getName());
                }
                if(item.getCount() < cartItem.getCount())
                {
                    cart.addMessage(
                            cartProduct.getId() + "번의 상품 내 상품 아이템 " + cartItem.getName() + "수량이 최대치" + item.getCount() + "으로 변경되었습니다."
                    );
                    cartItem.setCount(item.getCount());
                }
                if(!item.getPrice().equals(cartItem.getPrice()))
                {
                    cart.addMessage(
                            cartProduct.getId() + "번의 상품 내 상품 아이템 "+ cartItem.getName() +" 가격이 " + item.getPrice() + "으로 변경되었습니다."
                    );
                    cartItem.setPrice(item.getPrice());
                }
            }
        }
    }

    // 수량변경 또는 아이템 삭제
    public Cart updateCart(Long customerId, Cart cartForm)
    {
        Cart prevCart = redisClientService.get(customerId, Cart.class);
        if(prevCart != null)
        {
            Cart newCart = prevCart.clone();
            // product list만 변경 o
            newCart.setProductList(cartForm.getProductList());
            // 변경에 대한 검증 수행
            refreshCart(newCart, customerId);

            Cart redisCart = newCart.clone();
            redisCart.setMessages(new ArrayList<>());
            redisClientService.put(customerId, redisCart); // 해결했기 때문에 저장시에는 메시지 지우기

            return newCart;
        }
        else
        {
            Cart newCart = Cart.builder()
                    .customerId(customerId)
                    .productList(new ArrayList<>())
                    .messages(new ArrayList<>())
                    .build();

            newCart.addMessage("변경할 장바구니가 존재하지 않습니다.");
            return newCart;
        }
    }
}
