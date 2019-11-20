package com.findinpath.github.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Slimmed version of the Github API used only for test purposes for simulating the interaction with
 * an external API.
 * <p>
 */
public class GithubApi {

  public static final String API_URL = "https://api.github.com/";
  private final RestClient client;

  public GithubApi(RestClient client) {
    this.client = client;
  }

  /**
   * Lists all the repositories for the specified organisation name.
   *
   * @param organisationName the organisation name
   * @return the list of repositories for the organisation
   */
  List<GithubRepository> listOrganisationRepositories(String organisationName) {
    var result = client.getForEntity(API_URL + "orgs/" + organisationName + "/repos",
        GithubRepository[].class);
    return result == null ? Collections.emptyList() : Arrays.asList(result);
  }

  /**
   * Retrieves the information about the specified repository that belongs to the specified
   * organisation.
   *
   * @param organisationName the organisation name
   * @param repositoryName   the repository name
   * @return information about the repository
   */
  GithubRepository getOrganisationRepository(String organisationName, String repositoryName) {
    return client.getForEntity(API_URL + "orgs/" + organisationName + "/repos/" + repositoryName,
        GithubRepository.class);
  }
}
