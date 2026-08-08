package dev.vexsoft.core.gameplay.reactor.expression;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.data.EvaluationValue;
import com.ezylang.evalex.parser.ParseException;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/** EvalEx-backed expression compiler with reusable parsed syntax trees. */
@Dependencies
public final class VexExpressionService implements ExpressionService {

  private static final Pattern PLACEHOLDER = Pattern.compile(
      "%([a-z][a-z0-9]*(?:-[a-z0-9]+)*)%"
  );
  private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
  private final ExpressionConfiguration configuration;

  /** Creates the shared immutable EvalEx configuration. */
  public VexExpressionService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    configuration = ExpressionConfiguration.builder()
        .decimalPlacesRounding(16)
        .build();
  }

  @Override
  public CompiledExpression compile(final String expression) {
    String source = Objects.requireNonNull(expression, "expression").trim();
    if (source.isEmpty()) {
      throw new IllegalArgumentException("Expression must not be empty");
    }
    if (NUMBER.matcher(source).matches()) {
      return new ConstantExpression(new BigDecimal(source));
    }
    ParsedSource parsed = parsePlaceholders(source);
    Expression prototype = new Expression(parsed.expression, configuration);
    try {
      prototype.validate();
    } catch (ParseException exception) {
      throw new IllegalArgumentException("Invalid expression '" + source + '\'', exception);
    }
    return new EvalExExpression(source, prototype, parsed.placeholders);
  }

  private ParsedSource parsePlaceholders(final String source) {
    Matcher matcher = PLACEHOLDER.matcher(source);
    StringBuilder parsed = new StringBuilder(source.length());
    List<Placeholder> placeholders = new ArrayList<>();
    while (matcher.find()) {
      String variable = "variable" + placeholders.size();
      placeholders.add(new Placeholder(variable, matcher.group(1)));
      matcher.appendReplacement(parsed, variable);
    }
    matcher.appendTail(parsed);
    if (parsed.indexOf("%") >= 0) {
      throw new IllegalArgumentException("Invalid expression placeholder in '" + source + '\'');
    }
    return new ParsedSource(parsed.toString(), List.copyOf(placeholders));
  }

  @Value
  private static class ParsedSource {
    String expression;
    List<Placeholder> placeholders;
  }

  @Value
  private static class Placeholder {
    String variable;
    String contextName;
  }

  private static final class EvalExExpression implements CompiledExpression {

    private final String source;
    private final List<Placeholder> placeholders;
    private final ThreadLocal<Expression> expressions;

    private EvalExExpression(
        final String source,
        final Expression prototype,
        final List<Placeholder> placeholders
    ) {
      this.source = source;
      this.placeholders = placeholders;
      expressions = ThreadLocal.withInitial(() -> copy(prototype));
    }

    @Override
    public double evaluateNumber(final ReactorContext context) {
      return evaluate(context).getNumberValue().doubleValue();
    }

    @Override
    public boolean evaluateBoolean(final ReactorContext context) {
      return evaluate(context).getBooleanValue();
    }

    @Override
    public String evaluateString(final ReactorContext context) {
      return evaluate(context).getStringValue();
    }

    private EvaluationValue evaluate(final ReactorContext context) {
      ReactorContext checkedContext = Objects.requireNonNull(context, "context");
      Expression expression = expressions.get();
      for (Placeholder placeholder : placeholders) {
        Object value = checkedContext.getVariable(placeholder.contextName);
        if (value == null) {
          throw new IllegalStateException(
              "Expression '" + source + "' requires unavailable variable %"
                  + placeholder.contextName + "%"
          );
        }
        expression.with(placeholder.variable, value);
      }
      try {
        return expression.evaluate();
      } catch (EvaluationException | ParseException exception) {
        throw new IllegalStateException("Unable to evaluate expression '" + source + '\'', exception);
      }
    }

    private static Expression copy(final Expression prototype) {
      try {
        return prototype.copy();
      } catch (ParseException exception) {
        throw new IllegalStateException("Validated expression could not be copied", exception);
      }
    }
  }

  @Value
  private static class ConstantExpression implements CompiledExpression {
    BigDecimal value;

    @Override
    public double evaluateNumber(final ReactorContext context) {
      Objects.requireNonNull(context, "context");
      return value.doubleValue();
    }

    @Override
    public boolean evaluateBoolean(final ReactorContext context) {
      Objects.requireNonNull(context, "context");
      return value.signum() != 0;
    }

    @Override
    public String evaluateString(final ReactorContext context) {
      Objects.requireNonNull(context, "context");
      return value.toPlainString();
    }
  }
}
