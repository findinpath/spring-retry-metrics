package com.findinpath.github.api;

import static com.findinpath.github.api.GithubApi.API_URL;
import static com.findinpath.github.api.MeterUtils.getExactlyOneTimer;
import static com.findinpath.retry.config.GithubApiRetryConfiguration.API_METRIC_NAME;
import static com.findinpath.retry.config.GithubApiRetryConfiguration.API_RETRY_METRIC_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.findinpath.retry.config.GithubApiRetryConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

public class GithubApiRetryTest {

  private static final String ORGANISATION_NAME = "findinpath";
  private static final String SPRING_RETRY_MONITORING_REPOSITORY_NAME = "spring-retry-monitoring";
  private static final String BLOG_REPOSITORY_NAME = "blog";

  private MeterRegistry meterRegistry;
  private RestClient restClient;
  private GithubApi githubApi;

  @BeforeEach
  public void setup() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
        GithubApiRetryTest.TestConfiguration.class);
    meterRegistry = context.getBean(MeterRegistry.class);
    restClient = context.getBean(RestClient.class);
    githubApi = context.getBean(GithubApi.class);
  }

  @AfterEach
  public void after() {
    meterRegistry.getMeters()
        .forEach(meter -> this.meterRegistry.remove(meter));
  }

  @Test
  public void happyFlow() throws Exception {

    when(restClient.getForEntity(
        eq(API_URL + "orgs/" + ORGANISATION_NAME + "/repos"),
        eq(GithubRepository[].class))
    ).thenReturn(
        new GithubRepository[]{
            new GithubRepository(SPRING_RETRY_MONITORING_REPOSITORY_NAME,
                new URL("https://github.com/findinpath/spring-retry-monitoring"),
                false),
            new GithubRepository(BLOG_REPOSITORY_NAME,
                new URL("https://github.com/findinpath/blog"),
                false)
        }
    );

    var response = githubApi.listOrganisationRepositories(ORGANISATION_NAME);
    assertThat(response, hasSize(2));

    var meters = meterRegistry.getMeters();

    var bdsApiTimer = getExactlyOneTimer(meters, API_METRIC_NAME,
        Timer.class,
        Tag.of("exception", "none"),
        Tag.of("class", "GithubApi"),
        Tag.of("method", "listOrganisationRepositories"));

    var bdsApiRetryTimer = getExactlyOneTimer(meters, API_RETRY_METRIC_NAME,
        Timer.class,
        Tag.of("exception", "none"),
        Tag.of("class", "GithubApi"),
        Tag.of("method", "listOrganisationRepositories"));

    assertThat(bdsApiTimer.count(), equalTo(1L));
    assertThat(bdsApiRetryTimer.count(), equalTo(1L));
    assertThat(bdsApiRetryTimer.max(TimeUnit.MILLISECONDS),
        greaterThanOrEqualTo(bdsApiTimer.max(TimeUnit.MILLISECONDS)));
  }


  @Test
  public void firstApiOperationCallFails() throws Exception {
    var blogRepository = new GithubRepository(BLOG_REPOSITORY_NAME,
        new URL("https://github.com/findinpath/blog"),
        false);

    when(restClient.getForEntity(
        eq(API_URL + "orgs/" + ORGANISATION_NAME + "/repos/" + BLOG_REPOSITORY_NAME),
        eq(GithubRepository.class))
    ).thenThrow(new IllegalStateException("Internal server error"))
        .thenReturn(blogRepository);

    var repository = githubApi.getOrganisationRepository(ORGANISATION_NAME, BLOG_REPOSITORY_NAME);
    assertThat(repository, equalTo(blogRepository));

    // check that the monitoring works as expected
    var meters = meterRegistry.getMeters();
    var githubApiTimer = getExactlyOneTimer(meters, API_METRIC_NAME,
        Timer.class,
        Tag.of("exception", "none"),
        Tag.of("class", "GithubApi"),
        Tag.of("method", "getOrganisationRepository"));
    var githubApiExceptionTimer = getExactlyOneTimer(meters, API_METRIC_NAME,
        Timer.class,
        Tag.of("exception", "IllegalStateException"),
        Tag.of("class", "GithubApi"),
        Tag.of("method", "getOrganisationRepository"));
    var githubApiRetryTimer = getExactlyOneTimer(meters, API_RETRY_METRIC_NAME,
        Timer.class,
        Tag.of("class", "GithubApi"),
        Tag.of("method", "getOrganisationRepository"));

    assertThat(githubApiTimer.count(), equalTo(1L));
    assertThat(githubApiExceptionTimer.count(), equalTo(1L));
    assertThat(githubApiRetryTimer.count(), equalTo(1L));
    assertThat(githubApiRetryTimer.max(TimeUnit.MILLISECONDS),
        greaterThan(githubApiTimer.max(TimeUnit.MILLISECONDS)));
    assertThat(githubApiRetryTimer.max(TimeUnit.MILLISECONDS),
        greaterThan((double) GithubApiRetryTest.TestConfiguration.INITIAL_BACKOFF_TIME));
  }


  @Configuration
  @Import(GithubApiRetryConfiguration.class)
  protected static class TestConfiguration {

    static final int MAX_ATTEMPTS = 3;
    static final int INITIAL_BACKOFF_TIME = 30;


    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
      PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
      Properties properties = new Properties();
      properties.setProperty("github.api.retry.maxAttempts", Integer.toString(MAX_ATTEMPTS));
      properties.setProperty("github.api.retry.initialBackoffTime",
          Integer.toString(INITIAL_BACKOFF_TIME));
      pspc.setProperties(properties);
      return pspc;

    }

    @Bean
    public MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    public RestClient restClient() {
      return mock(RestClient.class);
    }

    @Bean
    public GithubApi githubApi(RestClient restClient) {
      return new GithubApi(restClient);
    }

  }
}
