package com.findinpath.retry.config;

import com.findinpath.micrometer.core.aop.TimedMethodInterceptor;
import com.findinpath.retry.listener.MicrometerRetryListenerSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.interceptor.MethodInvocationRetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Configuration class used for declaring the retry policy for making Github API calls. This
 * configuration relies heavily on Spring AOP for monitoring the latency of every call towards the
 * API as well as the combined duration (corresponding to the configured retry policy) of the API
 * calls including the retries.
 * <p>
 * The `github-api-aop-config.xml` resource is being referenced because in Java (at least at the
 * time of this writing) there's no support for method literals.
 */
@Configuration
@ImportResource("classpath:/github-api-aop-config.xml")
public class GithubApiRetryConfiguration {

  public static final String API_METRIC_NAME = "github_api";
  public static final String API_RETRY_METRIC_NAME = "github_api_retry";
  public final static double[] EXPORTED_PERCENTILES = {0.5, 0.75, 0.8, 0.9, 0.95, 0.99, 0.999};


  private static final String TIMED_API_METRIC_DESCRIPTION =
      "The time taken for completing the API calls";
  private static final String TIMED_API_WITH_RETRIES_METRIC_DESCRIPTION =
      "The time taken for completing the API calls with the retry policy (retries, backoff time)";

  private static RetryTemplate createRetryTemplateForRestTemplates(MeterRegistry meterRegistry,
      int maxAttempts,
      int initialBackoffTime,
      String metricName) {

    RetryTemplate retryTemplate = new RetryTemplate();

    // random jitter is important for ensuring that not all clients back off the same way.
    ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
    backOffPolicy.setInitialInterval(initialBackoffTime);
    retryTemplate.setBackOffPolicy(backOffPolicy);
    retryTemplate.setRetryPolicy(createSimpleRetryPolicy(maxAttempts));
    retryTemplate.setListeners(new RetryListener[]{
        new MicrometerRetryListenerSupport(meterRegistry, metricName)
    });
    return retryTemplate;
  }

  private static SimpleRetryPolicy createSimpleRetryPolicy(int maxAttempts) {
    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(maxAttempts);
    return retryPolicy;
  }

  @Bean(name = "githubApiRetryTemplate")
  public RetryTemplate retryTemplate(MeterRegistry meterRegistry,
      @Value("${github.api.retry.maxAttempts}") int maxAttempts,
      @Value("${github.api.retry.initialBackoffTime}") int initialBackoffTime) {

    return createRetryTemplateForRestTemplates(meterRegistry, maxAttempts, initialBackoffTime,
        API_METRIC_NAME);
  }

  @Bean(name = "githubApiRetryAdvice")
  public MethodInterceptor retryOperationsInterceptor(
      @Qualifier("githubApiRetryTemplate") RetryTemplate retryTemplate) {
    var interceptor = new MethodInvocationRetryOperationsInterceptor();
    interceptor.setRetryOperations(retryTemplate);
    return interceptor;
  }

  @Bean(name = "githubApiTimedAdvice")
  public MethodInterceptor timedMethodInterceptor(MeterRegistry meterRegistry) {
    return new TimedMethodInterceptor(API_METRIC_NAME,
        TIMED_API_METRIC_DESCRIPTION,
        EXPORTED_PERCENTILES,
        meterRegistry);
  }

  @Bean(name = "githubApiRetriesIncludedTimedAdvice")
  public MethodInterceptor retriesIncludedTimedMethodInterceptor(MeterRegistry meterRegistry) {
    return new TimedMethodInterceptor(API_RETRY_METRIC_NAME,
        TIMED_API_WITH_RETRIES_METRIC_DESCRIPTION,
        EXPORTED_PERCENTILES,
        meterRegistry);
  }
}