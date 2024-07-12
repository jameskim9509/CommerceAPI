package com.zerobase.orderApi.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.QProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom{

    private final JPAQueryFactory jpaQueryFactory;

    private final QProduct product = QProduct.product;

    @Override
    public List<Product> searchByName(String productName) {
        return jpaQueryFactory
                .select(product)
                .from(product)
                .where(productNameLike(productName))
                .fetch();
    }

    private BooleanExpression productNameLike(String productName)
    {
        return StringUtils.hasText(productName) ?
                product.name.contains(productName) : null;
    }
}
