package org.jackhuang.hmcl.ui.image.jxl.util.functional;

@FunctionalInterface
public interface FloatUnaryOperator {
    public float applyAsFloat(float x);

    public default FloatUnaryOperator compose(FloatUnaryOperator g) {
        return new FloatUnaryOperator() {
            public float applyAsFloat(float x) {
                return FloatUnaryOperator.this.applyAsFloat(g.applyAsFloat(x));
            }
        };
    }
}
