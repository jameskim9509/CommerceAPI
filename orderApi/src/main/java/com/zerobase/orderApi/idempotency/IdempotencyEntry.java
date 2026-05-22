package com.zerobase.orderApi.idempotency;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdempotencyEntry {
    private State state;
    private Integer httpStatus;
    private Object body;

    public enum State { IN_PROGRESS, COMPLETED }

    public static IdempotencyEntry inProgress() {
        return IdempotencyEntry.builder().state(State.IN_PROGRESS).build();
    }

    public static IdempotencyEntry completed(int httpStatus, Object body) {
        return IdempotencyEntry.builder()
                .state(State.COMPLETED)
                .httpStatus(httpStatus)
                .body(body)
                .build();
    }
}
