package com.akkasls.hackathon.indicators;


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MovingAveragesTest {

    @Test
    public void shouldCalculateSimpleMa() {
        var ma = MovingAverages.simple(5);
        assertThat(ma.value()).isEmpty();

        IntStream.range(1, 5).mapToObj(BigDecimal::valueOf).forEach(bd ->
                assertThat(ma.updateWith(bd).value()).isEmpty()
        );
        ma.updateWith(BigDecimal.valueOf(5));
        assertThat(ma.value()).contains(BigDecimal.valueOf(3));

        ma.updateWith(BigDecimal.valueOf(6));
        assertThat(ma.value()).contains(BigDecimal.valueOf(4));
    }

    @Test
    public void shouldCalculateExponentialMa() {
        var ma = MovingAverages.exponential(5);
        assertThat(ma.value()).isEmpty();

        IntStream.range(1, 5).mapToObj(BigDecimal::valueOf).forEach(bd ->
                assertThat(ma.updateWith(bd).value()).isEmpty()
        );
        ma.updateWith(BigDecimal.valueOf(5));
        assertThat(ma.value()).contains(BigDecimal.valueOf(3));

        ma.updateWith(BigDecimal.valueOf(6));
        assertThat(ma.value()).contains(BigDecimal.valueOf(3));
    }
}