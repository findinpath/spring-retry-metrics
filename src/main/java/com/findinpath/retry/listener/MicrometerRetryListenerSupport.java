package com.findinpath.retry.listener;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.interceptor.MethodInvocationRetryOperationsInterceptor;
import org.springframework.retry.listener.RetryListenerSupport;

/**
 * Implementation of a spring-retry {@link RetryListenerSupport} used for monitoring purposes of how
 * the retry policy is applied on the service method calls.
 * <p>
 * Instances of this retry listener class will issue two kind of metrics:
 * <pre>
 *     <ul>
 *         <li>metricName_retries : counts the amount of retries made for completing a specific method call</li>
 *         <li>metricName_failures: counts the amount of failures made when failing (even with retries) a specific method call</li>
 *     </ul>
 * </pre>
 */
public class MicrometerRetryListenerSupport extends RetryListenerSupport {

  private static final String NAME_TAG_NAME = "name";
  private static final String CLASS_TAG_NAME = "class";
  private static final String METHOD_TAG_NAME = "method";
  private static final String UNKNOWN_NAME = "unknown";
  private static final String RETRY_TAG_NAME = "retry";
  private static final String EXCEPTION_TAG_NAME = "exception";
  private static final String NONE = "none";


  private final MeterRegistry meterRegistry;
  private final String retriesMetricName;
  private final String failuresMetricName;

  private final ConcurrentMap<CounterKey, Counter> retriesCounterMap = new ConcurrentHashMap<>();
  private final ConcurrentMap<CounterKey, Counter> failuresCounterMap = new ConcurrentHashMap<>();


  /**
   * The constructor for the class.
   *
   * @param meterRegistry the monitoring registry
   * @param metricName    the name prefix for the metric
   */
  public MicrometerRetryListenerSupport(MeterRegistry meterRegistry, String metricName) {
    this.meterRegistry = meterRegistry;
    this.retriesMetricName = metricName + "_retries";
    this.failuresMetricName = metricName + "_failures";
  }

  @Override
  public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
      Throwable throwable) {
    if (throwable != null) {
      var failuresCounter = failuresCounterMap.computeIfAbsent(
          new CounterKey(getContextName(context), context.getRetryCount(), throwable),
          MicrometerRetryListenerSupport.this::getFailuresCounter);

      failuresCounter.increment();
    } else {
      var retriesCounter = retriesCounterMap.computeIfAbsent(
          new CounterKey(getContextName(context), context.getRetryCount(),
              context.getLastThrowable()),
          MicrometerRetryListenerSupport.this::getRetriesCounter);

      retriesCounter.increment();
    }
  }


  private ContextMetadata getContextName(RetryContext retryContext) {
    var nameAttribute = retryContext.getAttribute(RetryContext.NAME);
    var name = nameAttribute == null ? UNKNOWN_NAME : nameAttribute.toString();
    var methodInvocation = retryContext
        .getAttribute(MethodInvocationRetryOperationsInterceptor.METHOD_INVOCATION);

    if (methodInvocation == null) {
      return new ContextMetadata(name);
    }

    var method = ((MethodInvocation) methodInvocation).getMethod();

    var methodName = method.getName();
    var className = method.getDeclaringClass().getSimpleName();

    return new ContextMetadata(name, className, methodName);
  }


  private Counter getRetriesCounter(CounterKey key) {
    return Counter.builder(retriesMetricName)
        .description("Counts the calls made to a service method with the retry policy")
        .tag(NAME_TAG_NAME, key.name)
        .tag(CLASS_TAG_NAME, key.className)
        .tag(METHOD_TAG_NAME, key.methodName)
        .tag(RETRY_TAG_NAME, Integer.toString(key.retryCount))
        .tag(EXCEPTION_TAG_NAME, key.lastThrowableClassName)
        .register(meterRegistry);
  }

  private Counter getFailuresCounter(CounterKey key) {
    return Counter.builder(failuresMetricName)
        .description("Counts the failed calls made to a service method")
        .tag(NAME_TAG_NAME, key.name)
        .tag(CLASS_TAG_NAME, key.className)
        .tag(METHOD_TAG_NAME, key.methodName)
        .tag(EXCEPTION_TAG_NAME, key.lastThrowableClassName)
        .register(meterRegistry);
  }

  /**
   * Key class used in the mappings for the counters.
   */
  private static class CounterKey {

    private String name;
    private String methodName;
    private String className;
    private int retryCount;
    private String lastThrowableClassName;

    CounterKey(ContextMetadata contextMetadata, int retryCount, Throwable e) {
      this.name = contextMetadata.name;
      this.methodName = contextMetadata.methodName;
      this.className = contextMetadata.className;
      this.retryCount = retryCount;
      this.lastThrowableClassName = e == null ? NONE : e.getClass().getSimpleName();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CounterKey that = (CounterKey) o;
      return retryCount == that.retryCount &&
          Objects.equals(name, that.name) &&
          Objects.equals(lastThrowableClassName, that.lastThrowableClassName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, retryCount, lastThrowableClassName);
    }
  }

  private static class ContextMetadata {

    private String name;
    private String className;
    private String methodName;

    public ContextMetadata(String name) {
      this.name = name;
      this.className = NONE;
      this.methodName = NONE;
    }

    public ContextMetadata(String name, String className, String methodName) {
      this.name = name;
      this.className = className;
      this.methodName = methodName;
    }
  }
}
