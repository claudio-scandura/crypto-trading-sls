package com.akkasls.hackathon.indicators;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.Optional;

public class MovingAverages {

    public static MovingAverage simple(int period) {
        return new SimpleMovingAverage(period);
    }

    public static MovingAverage exponential(int period) {
        return new ExponentialMovingAverage(period);
    }

    public static abstract class MovingAverage {
        public final int period;
        private BigDecimal value;
        private final LinkedList<BigDecimal> observations = new LinkedList<>();

        protected MovingAverage(int period) {
            this.period = period;
        }

        private BigDecimal period() {
            return BigDecimal.valueOf(period);
        }

        public Optional<BigDecimal> value() {
            return Optional.ofNullable(value);
        }

        public MovingAverage updateWith(BigDecimal observation) {
            if (observations.size() >= period) {
                return updateWith(value, observation);
            } else {
                observations.push(observation);
                if (observations.size() == period) {
                    value = observations.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(period(), RoundingMode.HALF_EVEN);
                }
            }
            return this;
        }

        protected abstract MovingAverage updateWith(BigDecimal currentValue, BigDecimal observation);
    }

    private static class SimpleMovingAverage extends MovingAverage {

        public SimpleMovingAverage(int period) {
            super(period);
        }

        @Override
        protected MovingAverage updateWith(BigDecimal currentValue, BigDecimal observation) {
            var expiring = super.observations.removeLast();
            super.observations.push(observation);
            super.value = (currentValue.multiply(super.period()).subtract(expiring).add(observation))
                    .divide(super.period(), RoundingMode.HALF_EVEN);
            return this;
        }
    }

    private static class ExponentialMovingAverage extends MovingAverage {

        public ExponentialMovingAverage(int period) {
            super(period);
        }

        @Override
        protected MovingAverage updateWith(BigDecimal currentValue, BigDecimal observation) {
            var k = BigDecimal.valueOf(2.0 / (double) (1 + super.period));
            super.value = observation.multiply(k).add(currentValue.multiply(BigDecimal.ONE.subtract(k)));
            return this;
        }
    }
}
