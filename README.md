Monitoring for spring-retry
===========================

This demo showcases how to add monitoring on the [spring-retry](https://github.com/spring-projects/spring-retry) functionality.


The project [spring-retry](https://github.com/spring-projects/spring-retry) offers both declarative and
imperative retry support for [spring](https://spring.io/) applications.

Below is presented a simple code snippet to get an
idea about how the imperative retry support can be
configured for a project:

```java
RetryTemplate template = RetryTemplate.builder()
				.maxAttempts(3)
				.fixedBackoff(1000)
				.retryOn(RemoteAccessException.class)
				.build();

template.execute(ctx -> {
    // ... do something
});
```

Coupling together spring AOP and spring-retry gives
the ability to perform retries on all the public method calls of a specific class. 
This could come pretty handy when dealing with a class which exposes the API of an external service. 

Due to various reasons, every now and then it happens that an API call doesn't succeed, 
but when trying again everything works just fine.  

What this project showcases is the ability to monitor via [micrometer](https://micrometer.io/)
library:

- how many calls to the external API were successful
- how many calls to the external API were retried once, twice, three times
- how many calls to the external API have failed and with which exception class 


The test class `com.findinpath.github.api.GithubApiRetryTest` provides concrete test cases
for proving how the monitoring of the retry functionality works.

For the sake of a concrete example, this project makes use of a slimmed mocked version of the
Github API. No actual calls towards [Github API](https://api.github.com/) are made during the
tests of this project.   

Below is presented a test scenario from the project for showing at work 
both the spring-retry functionality (the Github repository details are
retrieved successfully, even though the first call to the Github API fails)
and the monitoring of the spring-retry functionality on the methods advised
via spring AOP for the class `com.findinpath.github.api.GithubApi` 


```java
  @Test
  public void firstApiOperationCallFails() throws Exception {
    var blogRepository = new GithubRepository(BLOG_REPOSITORY_NAME,
        new URL("https://github.com/findinpath/blog"),
        false);

    when(restClient.getForEntity(
        eq(API_URL + "orgs/" + ORGANISATION_NAME + "/repos/" + BLOG_REPOSITORY_NAME),
        eq(GithubRepository.class))
    )
    .thenThrow(new IllegalStateException("Internal server error"))
    .thenReturn(blogRepository);

    var repository = githubApi.getOrganisationRepository(ORGANISATION_NAME, BLOG_REPOSITORY_NAME);
    assertThat(repository, equalTo(blogRepository));

    // check that the monitoring works as expected
    var meters = meterRegistry.getMeters();
    var githubApiTimer = getExactlyOneMeter(meters, API_METRIC_NAME,
        Timer.class,
        Tag.of("exception", "none"),
        Tag.of("class", "GithubApi"),
        Tag.of("method", "getOrganisationRepository"));
    var githubApiExceptionTimer = getExactlyOneMeter(meters, API_METRIC_NAME,
        Timer.class,
        Tag.of("exception", "IllegalStateException"),
        Tag.of("class", "GithubApi"),
        Tag.of("method", "getOrganisationRepository"));
    var githubApiRetryTimer = getExactlyOneMeter(meters, API_RETRY_METRIC_NAME,
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

    var bdsApiRetriesCounter = getExactlyOneMeter(meters, API_METRIC_NAME + "_retries",
        Counter.class,
        Tag.of(MicrometerRetryListenerSupport.CLASS_TAG_NAME, "GithubApi"),
        Tag.of(MicrometerRetryListenerSupport.METHOD_TAG_NAME, "getOrganisationRepository"),
        Tag.of(MicrometerRetryListenerSupport.RETRY_TAG_NAME, "1"),
        Tag.of(MicrometerRetryListenerSupport.EXCEPTION_TAG_NAME, "IllegalStateException"));
    assertThat(bdsApiRetriesCounter.count(), equalTo((double) 1));

  }
```


## Spring AOP configuration 

The following Spring AOP configuration serves for the following purposes:

- times how long each Github API call takes to complete
- adds spring-retry functionality on all exposed Github API calls
- times how long each Github API call takes to complete including internal 
retries (in case that the first,second, .. call to the API doesn't succeed)   

```xml
  <aop:config>
    <aop:pointcut id="github-api-calls"
      expression="execution(* com.findinpath.github.api.GithubApi.*(..))  "/>

    <!--
    the githubApiRetriesIncludedTimedAdvice advice wraps
    the githubApiRetryAdvice advice and this it can provide
    timing information for the duration of the API call
    including retries
     -->
    <aop:advisor pointcut-ref="github-api-calls"
      advice-ref="githubApiRetriesIncludedTimedAdvice" order="1"/>
    <aop:advisor pointcut-ref="github-api-calls"
      advice-ref="githubApiRetryAdvice" order="2"/>
    <!--
    timing advice for each (retries are not taken into account)
    of the Github API calls.
    -->
    <aop:advisor pointcut-ref="github-api-calls"
      advice-ref="githubApiTimedAdvice" order="3"/>

  </aop:config>
```

Notice on the configuration above that several Spring AOP advisors are stacked on top of each
other. The precedence of the advisor is determined by the `order` parameter of the advisor. 

## Spring-retry enhancements

At the time of this writing, [spring-retry](https://github.com/spring-projects/spring-retry) 
library in the version `1.2.4.RELEASE` doesn't provide the ability to retrieve information
about the invoked method (see `org.aopalliance.intercept.MethodInvocation`) in the spring AOP context.
See the corresponding Github [issue](https://github.com/spring-projects/spring-retry/issues/119)

This limitation doesn't allow to access the invoked class and method name in 
the concrete implementations of the `org.springframework.retry.listener.RetryListenerSupport`
defined for the spring-retry's `RetryTemplate`.
What this means is that when a spring-retry failure occurs on a specific API call, we only would
know in the monitoring that one of the API calls failed, but not exactly which.
For our concrete example, if Github API has several exposed endpoints, it would be surely important
to know via monitoring dashboards which one of these endpoints is causing failures in our application.

In order to enhance the monitoring of the methods advised with spring-retry functionality, this 
project has performed a little enhancement on the default [spring-retry](https://github.com/spring-projects/spring-retry) functionality by adding the `MethodInvocation` of the advided method to the retry context.
See the code source for the class 
`org.springframework.retry.interceptor.MethodInvocationRetryOperationsInterceptor`
for more details.

## Run the project

Run the command

```bash
mvn clean install
```

for executing the tests from this project.