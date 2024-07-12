package com.zerobase.orderApi.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.domain.QProductItem;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class ProductItemRepositoryImpl implements ProductItemRepositoryCustom{

    private final JPAQueryFactory jpaQueryFactory;
    private final QProductItem productItem = QProductItem.productItem;

    @Override
    public List<ProductItem> searchByCond(
            String itemName, Integer priceMin, Integer priceMax, Pageable pageable
    ) {
        return jpaQueryFactory
                .select(productItem)
                .from(productItem)
                .where(
                        itemNameLike(itemName),
                        priceGoe(priceMin),
                        priceLoe(priceMax)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    private BooleanExpression priceGoe(Integer priceMin)
    {
        return priceMin == null ? null : productItem.price.goe(priceMin);
    }

    private BooleanExpression priceLoe(Integer priceMax)
    {
        return priceMax == null ? null : productItem.price.loe(priceMax);
    }

    private BooleanExpression itemNameLike(String itemName)
    {
        return StringUtils.hasText(itemName) ?
                productItem.name.contains(itemName) : null;
    }
}
