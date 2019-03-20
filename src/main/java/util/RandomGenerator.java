package util;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface RandomGenerator {
    float random();

    static RandomGenerator constant(float c) {
        return () -> c;
    }

    static RandomGenerator uniform(float min, float max) {
        return () -> (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    static RandomGenerator normal(float mu, float sigma) {
        // if z ~ N(0, 1), then (z * sigma + mu) ~ N(mu, sigma^2)
        return () ->
                mu + (float) ThreadLocalRandom.current().nextGaussian() * sigma;
    }
}