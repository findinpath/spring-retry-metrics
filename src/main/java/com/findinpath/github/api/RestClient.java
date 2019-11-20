package com.findinpath.github.api;

/**
 * Interface used for simulating the interaction with a REST resource.
 * <p>
 * Due to the fact that the Github API is mocked in the tests, no concrete implementation will be
 * provided for this class in the source code of the project.
 */
public interface RestClient {

  <T> T getForEntity(String url, Class<T> responseType);
}
