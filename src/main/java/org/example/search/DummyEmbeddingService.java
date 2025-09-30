package org.example.search;

import java.nio.charset.StandardCharsets;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Deterministic toy embedding generator based on text hash.
 */
public final class DummyEmbeddingService implements EmbeddingService {

    private final int dim;

    public DummyEmbeddingService() {
        this(8);
    }

    public DummyEmbeddingService(int dim) {
        this.dim = Math.max(4, dim);
    }

    @Override
    public double[] embed(String text) {
        final byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        long seed = 1125899906842597L; // prime
        for (final byte b : bytes) {
            seed = 31 * seed + b;
        }
        final RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        final double[] v = new double[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = rng.nextDouble(-1.0, 1.0);
        }
        return v;
    }
}
