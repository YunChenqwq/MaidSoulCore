package com.yunchen.maidsoulcore.core.affect;

import java.util.Random;

public final class OuProcess {
    public double value;
    public double baseline;
    public double reversionSpeed;
    public double volatility;
    private final boolean signed;

    public OuProcess(double value, double baseline, double reversionSpeed, double volatility) {
        this(value, baseline, reversionSpeed, volatility, false);
    }

    public OuProcess(double value, double baseline, double reversionSpeed, double volatility, boolean signed) {
        this.value = value;
        this.baseline = baseline;
        this.reversionSpeed = reversionSpeed;
        this.volatility = volatility;
        this.signed = signed;
        clamp();
    }

    public void bump(double amount) {
        value += amount;
        clamp();
    }

    public void step(double hours, Random random) {
        double remaining = Math.max(0.0D, hours);
        if (remaining <= 0.0D) {
            clamp();
            return;
        }
        while (remaining > 0.0D) {
            double dt = Math.min(1.0D, remaining);
            double drift = reversionSpeed * (baseline - value) * dt;
            double noise = 0.0D;
            if (volatility > 0.0D && random != null) {
                noise = volatility * Math.sqrt(dt) * random.nextGaussian();
            }
            value += drift + noise;
            clamp();
            remaining -= dt;
        }
    }

    public void reset(double newValue) {
        value = newValue;
        clamp();
    }

    private void clamp() {
        value = signed ? AffectProfile.clampSigned(value) : AffectProfile.clamp01(value);
        baseline = signed ? AffectProfile.clampSigned(baseline) : AffectProfile.clamp01(baseline);
        reversionSpeed = Math.max(0.0D, reversionSpeed);
        volatility = Math.max(0.0D, volatility);
    }
}
