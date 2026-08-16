package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.FittedParameter.Format;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.InvalidForecastParameterException;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.ParameterSpec;
import com.stockforecast.forecasting.domain.model.ExponentialSmoothing.Solution;
import com.stockforecast.forecasting.domain.model.ExponentialSmoothing.Weights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Exponential smoothing of log prices (see {@link ExponentialSmoothing} for the equations): recent observations get
 * geometrically more weight than older ones. Variants: simple (level only), Holt's linear trend and damped trend;
 * {@code AUTO} fits all three and keeps the one with the lowest AICc, which penalises extra parameters.
 *
 * <p>Smoothing weights are estimated by minimising one-step-ahead squared errors unless fixed by the request (or
 * when optimisation is disabled, in which case configured defaults apply). Holt-Winters is not offered: daily stock
 * prices have no stable seasonal cycle for a seasonal component to capture.
 */
public final class ExponentialSmoothingForecastModel extends CalibratedPricePathModel {

    public static final String VARIANT = "variant";
    public static final String LOOKBACK = "lookback";
    public static final String ALPHA = "alpha";
    public static final String BETA = "beta";
    public static final String PHI = "phi";
    static final int MINIMUM_FIT_OBSERVATIONS = 60;
    static final int MAXIMUM_LOOKBACK = 1260;
    /** Forecasts from the first few states of the fitted window mostly reflect the initial state; skip them. */
    static final int WARM_UP = 10;

    public enum Variant {
        AUTO,
        SIMPLE,
        HOLT,
        DAMPED
    }

    /**
     * @param optimise when {@code false}, weights not supplied by a request use the defaults instead of being estimated
     */
    public record Settings(int defaultLookback, boolean optimise, double defaultAlpha, double defaultBeta,
                           double defaultPhi) {
    }

    private final Settings settings;
    private final List<ParameterSpec> parameters;

    public ExponentialSmoothingForecastModel(EmpiricalIntervalCalibrator calibrator, Settings settings) {
        super(calibrator);
        this.settings = settings;
        this.parameters = List.of(
                ParameterSpec.choice(VARIANT, "Variant", Variant.AUTO.name(),
                        Arrays.stream(Variant.values()).map(Enum::name).toList(),
                        "SIMPLE (level only), HOLT (linear trend), DAMPED (trend that fades) or AUTO (lowest AICc)"),
                ParameterSpec.integer(LOOKBACK, "Lookback (trading days)", settings.defaultLookback(),
                        MINIMUM_FIT_OBSERVATIONS, MAXIMUM_LOOKBACK,
                        "Most recent closes the model is fitted to (fewer when less history is available)"),
                ParameterSpec.optionalDecimal(ALPHA, "Level smoothing (α)", ExponentialSmoothing.MIN_WEIGHT,
                        ExponentialSmoothing.MAX_WEIGHT, "Weight of the newest observation in the level; estimated "
                                + "when omitted"),
                ParameterSpec.optionalDecimal(BETA, "Trend smoothing (β)", ExponentialSmoothing.MIN_WEIGHT,
                        ExponentialSmoothing.MAX_WEIGHT, "How quickly the trend adapts (HOLT and DAMPED); estimated "
                                + "when omitted"),
                ParameterSpec.optionalDecimal(PHI, "Trend damping (φ)", ExponentialSmoothing.MIN_PHI,
                        ExponentialSmoothing.MAX_PHI, "How quickly the trend fades (DAMPED); estimated when omitted"));
    }

    @Override
    public ForecastModelType type() {
        return ForecastModelType.EXPONENTIAL_SMOOTHING;
    }

    @Override
    public List<ParameterSpec> parameters() {
        return parameters;
    }

    @Override
    public ModelParameters resolveParameters(ModelParameters requested) {
        ModelParameters resolved = super.resolveParameters(requested);
        Variant variant = Variant.valueOf(resolved.choice(VARIANT));
        for (String weight : List.of(ALPHA, BETA, PHI)) {
            if (resolved.get(weight).isEmpty()) {
                continue;
            }
            if (variant == Variant.AUTO) {
                throw new InvalidForecastParameterException(weight,
                        "can only be fixed for an explicit variant (SIMPLE, HOLT or DAMPED)");
            }
            boolean used = weight.equals(ALPHA) || (weight.equals(BETA) && variant != Variant.SIMPLE)
                    || (weight.equals(PHI) && variant == Variant.DAMPED);
            if (!used) {
                throw new InvalidForecastParameterException(weight,
                        "is not used by the %s variant".formatted(variant.name()));
            }
        }
        return resolved;
    }

    @Override
    public int minimumObservations(ModelParameters parameters) {
        return MINIMUM_FIT_OBSERVATIONS;
    }

    @Override
    protected Fit fit(double[] closes, ModelParameters parameters) {
        int from = Math.max(0, closes.length - parameters.integer(LOOKBACK));
        double[] logCloses = new double[closes.length - from];
        for (int i = 0; i < logCloses.length; i++) {
            logCloses[i] = Math.log(closes[from + i]);
        }
        Variant requested = Variant.valueOf(parameters.choice(VARIANT));
        List<Candidate> candidates = (requested == Variant.AUTO
                ? List.of(Variant.SIMPLE, Variant.HOLT, Variant.DAMPED) : List.of(requested))
                .stream().map(variant -> fitVariant(logCloses, variant, parameters)).toList();
        Candidate chosen = candidates.stream().min(Comparator.comparingDouble(Candidate::aicc)).orElseThrow();
        Solution solution = chosen.solution();

        PointForecaster forecaster = new PointForecaster() {
            @Override
            public int firstOrigin() {
                return from + Math.min(WARM_UP, logCloses.length - 1);
            }

            @Override
            public double[] forecastFrom(int origin, int steps) {
                double[] forecast = solution.forecast(origin - from, steps);
                for (int k = 0; k < steps; k++) {
                    forecast[k] = Math.exp(forecast[k]);
                }
                return forecast;
            }
        };
        return new Fit(forecaster, specification(chosen, requested == Variant.AUTO),
                fitted(chosen, logCloses.length), notes(chosen, candidates, requested));
    }

    private Candidate fitVariant(double[] logCloses, Variant variant, ModelParameters parameters) {
        ExponentialSmoothing.Variant internal = ExponentialSmoothing.Variant.valueOf(variant.name());
        Double alpha = fixedWeight(parameters, ALPHA, settings.defaultAlpha());
        Double beta = fixedWeight(parameters, BETA, settings.defaultBeta());
        Double phi = fixedWeight(parameters, PHI, settings.defaultPhi());
        Weights weights = ExponentialSmoothing.estimate(logCloses, internal, alpha, beta, phi);
        int estimated = (alpha == null ? 1 : 0) + (variant != Variant.SIMPLE && beta == null ? 1 : 0)
                + (variant == Variant.DAMPED && phi == null ? 1 : 0);
        Solution solution = ExponentialSmoothing.solve(logCloses, weights);
        return new Candidate(variant, solution, solution.aicc(estimated), estimated > 0);
    }

    /** A weight from the request, else the configured default when optimisation is off, else {@code null}. */
    private Double fixedWeight(ModelParameters parameters, String name, double fallback) {
        OptionalDouble requested = parameters.decimal(name);
        if (requested.isPresent()) {
            return requested.getAsDouble();
        }
        return settings.optimise() ? null : fallback;
    }

    private static String specification(Candidate chosen, boolean automatic) {
        Weights weights = chosen.solution().weights();
        String description = switch (chosen.variant()) {
            case SIMPLE -> "Simple exponential smoothing (α=%s)".formatted(format(weights.alpha()));
            case HOLT -> "Holt linear trend (α=%s, β=%s)".formatted(format(weights.alpha()), format(weights.beta()));
            case DAMPED -> "Damped trend (α=%s, β=%s, φ=%s)".formatted(format(weights.alpha()),
                    format(weights.beta()), format(weights.phi()));
            case AUTO -> throw new IllegalStateException("AUTO is resolved to a concrete variant");
        };
        return automatic ? description + ", selected by AICc" : description;
    }

    private static List<FittedParameter> fitted(Candidate chosen, int observations) {
        Solution solution = chosen.solution();
        Weights weights = solution.weights();
        List<FittedParameter> fitted = new ArrayList<>();
        addIfFinite(fitted, ALPHA, "Level smoothing (α)", weights.alpha(), Format.NUMBER);
        if (weights.trend()) {
            addIfFinite(fitted, BETA, "Trend smoothing (β)", weights.beta(), Format.NUMBER);
            if (chosen.variant() == Variant.DAMPED) {
                addIfFinite(fitted, PHI, "Trend damping (φ)", weights.phi(), Format.NUMBER);
            }
            addIfFinite(fitted, "trendPerDay", "Current trend per day",
                    Math.exp(solution.trends()[observations - 1]) - 1, Format.PERCENT);
        }
        addIfFinite(fitted, "aicc", "AICc", chosen.aicc(), Format.NUMBER);
        addIfFinite(fitted, "observations", "Observations fitted", observations, Format.NUMBER);
        return fitted;
    }

    private static List<String> notes(Candidate chosen, List<Candidate> candidates, Variant requested) {
        List<String> notes = new ArrayList<>();
        notes.add(chosen.estimated()
                ? "Smoothing weights were estimated by minimising one-step-ahead squared errors on the fitted window."
                : "Smoothing weights were fixed rather than estimated from the data.");
        if (requested == Variant.AUTO) {
            Map<Variant, Double> scores = candidates.stream()
                    .collect(Collectors.toMap(Candidate::variant, Candidate::aicc));
            notes.add("AICc (lower is better): simple %s, Holt %s, damped %s.".formatted(
                    format(scores.get(Variant.SIMPLE)), format(scores.get(Variant.HOLT)),
                    format(scores.get(Variant.DAMPED))));
        }
        return notes;
    }

    /** Weights with up to 4 decimals (so bounds such as 0.0001 stay visible), large scores with 1. */
    private static String format(double value) {
        int scale = Math.abs(value) >= 100 ? 1 : 4;
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private record Candidate(Variant variant, Solution solution, double aicc, boolean estimated) {
    }
}
