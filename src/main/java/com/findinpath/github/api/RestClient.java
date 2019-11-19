package com.findinpath.github.api;

public interface RestClient {

  <T> T getForEntity(String url, Class<T> responseType);
}
