package com.findinpath.micrometer.core.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ProxyMethodInvocation;

/**
 * This utility class duplicates largely the code defined in {@link io.micrometer.core.aop.TimedAspect}
 * and adapts it in order to be able to work with AOP's method interceptors.
 *
 * @see io.micrometer.core.aop.TimedAspect
 */
public class TimedMethodInterceptor implements MethodInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimedMethodInterceptor.class);

  private static final String EXCEPTION_TAG = "exception";

  private final MeterRegistry registry;
  private final String metricName;
  private final String metricDescription;
  private final double[] exportedPercentiles;

  public TimedMethodInterceptor(String metricName,
      String metricDescription,
      double[] exportedPercentiles,
      MeterRegistry registry) {
    this.metricName = metricName;
    this.metricDescription = metricDescription;
    this.exportedPercentiles = exportedPercentiles;
    this.registry = registry;

    Timer.builder(metricName)
        .description(metricDescription)
        .publishPercentiles(exportedPercentiles)
        .register(registry);
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {

    if (invocation instanceof ProxyMethodInvocation) {
      Method method = invocation.getMethod();

      Timer.Sample sample = Timer.start(registry);
      String exceptionClass = "none";
      try {
        return ((ProxyMethodInvocation) invocation).invocableClone().proceed();
      } catch (Exception ex) {
        exceptionClass = ex.getClass().getSimpleName();
        throw ex;
      } finally {
        try {

          var methodName = method.getName();
          var className = method.getDeclaringClass().getSimpleName();

          sample.stop(Timer.builder(metricName)
              .description(metricDescription)
              .tags(EXCEPTION_TAG, exceptionClass)
              .tag("class", className)
              .tag("method", methodName)
              .publishPercentiles(exportedPercentiles)
              .register(registry));
        } catch (Exception e) {
          // ignoring on purpose
          LOGGER.error("Exception occurred while creating timer for the method "
              + method.toGenericString(), e);
        }
      }
    } else {
      throw new IllegalStateException(
          "MethodInvocation of the wrong type detected - this should not happen with Spring AOP, " +
              "so please raise an issue if you see this exception");
    }

  }
}
