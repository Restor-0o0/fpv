package org.restor.fpv.compat;

public interface StreamCodec<B, T> {
    void encode(B buf, T value);

    T decode(B buf);

    static <B, T> StreamCodec<B, T> of(Encoder<B, T> encoder, Decoder<B, T> decoder) {
        return new StreamCodec<>() {
            @Override
            public void encode(B buf, T value) {
                encoder.encode(buf, value);
            }

            @Override
            public T decode(B buf) {
                return decoder.decode(buf);
            }
        };
    }

    @FunctionalInterface
    interface Encoder<B, T> {
        void encode(B buf, T value);
    }

    @FunctionalInterface
    interface Decoder<B, T> {
        T decode(B buf);
    }
}
