package com.findinpath.github.api;

import java.net.URL;
import java.util.Objects;

/**
 * This class models information about a Github repository.
 */
public class GithubRepository {

  private String name;
  private URL url;
  private boolean privateRepository;

  public GithubRepository() {
  }

  public GithubRepository(String name, URL url, boolean privateRepository) {
    this.name = name;
    this.url = url;
    this.privateRepository = privateRepository;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public URL getUrl() {
    return url;
  }

  public void setUrl(URL url) {
    this.url = url;
  }

  public boolean isPrivateRepository() {
    return privateRepository;
  }

  public void setPrivateRepository(boolean privateRepository) {
    this.privateRepository = privateRepository;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GithubRepository that = (GithubRepository) o;
    return privateRepository == that.privateRepository &&
        Objects.equals(name, that.name) &&
        Objects.equals(url, that.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, url, privateRepository);
  }
}
