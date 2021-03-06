/*-
 * -\-\-
 * Spydra
 * --
 * Copyright (C) 2016 - 2018 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.spydra.util;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.ServiceOptions;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

public abstract class GcpConfiguration {

  public static GcpConfiguration create() {
    return useApplicationDefaultCredentials()
           ? new GcpConfigurationFromDefaultCredentials()
           : new GcpConfigurationFromServiceAccountKey();
  }

  private static boolean useApplicationDefaultCredentials() {
    return jsonCredentialPath() == null;
  }

  static String jsonCredentialPath() {
    return System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
  }

  public abstract String getProjectId();

  public abstract Optional<String> getUserId();

  public Optional<String> getJsonCredentialsPath() {
    return Optional.ofNullable(jsonCredentialPath());
  }

  public abstract Credentials getCredentials();

  public abstract GoogleCredential getCredential();

  private static class GcpConfigurationFromDefaultCredentials extends GcpConfiguration {

    @Override
    public String getProjectId() {
      return ServiceOptions.getDefaultProjectId();
    }

    @Override
    public Credentials getCredentials() {
      try {
        return GoogleCredentials.getApplicationDefault();
      } catch (IOException e) {
        throw new RuntimeException("Failed to load application default credentials", e);
      }
    }

    @Override
    public Optional<String> getUserId() {
      return Optional.empty();
    }

    @Override
    public GoogleCredential getCredential() {
      try {
        return GoogleCredential.getApplicationDefault();
      } catch (IOException e) {
        throw new RuntimeException("Failed to load application default GoogleCredential", e);
      }
    }
  }

  private static class GcpConfigurationFromServiceAccountKey extends GcpConfiguration {

    @Override
    public String getProjectId() {
      return projectFromJsonCredential(credentialJsonFromEnv());
    }

    private String credentialJsonFromEnv() {
      String jsonFile = jsonCredentialPath();
      if (jsonFile == null || !Files.exists(Paths.get(jsonFile))) {
        throw new IllegalArgumentException(
            "GOOGLE_APPLICATION_CREDENTIALS needs to be set and point to a valid credential json");
      }
      try {
        return new String(Files.readAllBytes(Paths.get(jsonFile)));
      } catch (IOException e) {
        throw new RuntimeException("Failed to read " + jsonFile, e);
      }
    }

    private String projectFromJsonCredential(String json) {
      try {
        return JsonPath.read(json, "$.project_id");
      } catch (PathNotFoundException ex) {
        throw new RuntimeException("Could not parse project_id from credentials.");
      }
    }

    @Override
    public Credentials getCredentials() {
      try {
        return ServiceAccountCredentials.fromStream(
            new ByteArrayInputStream(credentialJsonFromEnv().getBytes("UTF-8")));
      } catch (IOException e) {
        throw new RuntimeException("Failed to load service account credentials from file", e);
      }
    }

    private String userIdFromJsonCredential(String json) {
      try {
        return JsonPath.read(json, "$.client_email");
      } catch (PathNotFoundException ex) {
        throw new IllegalArgumentException(
            "No valid credentials (service account) were available to forward to the cluster.", ex);
      }
    }

    @Override
    public Optional<String> getUserId() {
      return Optional.of(userIdFromJsonCredential(credentialJsonFromEnv()));
    }

    @Override
    public GoogleCredential getCredential() {
      try {
        return GoogleCredential.fromStream(
            new ByteArrayInputStream(credentialJsonFromEnv().getBytes()));
      } catch (IOException e) {
        throw new RuntimeException("Failed to load GoogleCredential from json", e);
      }
    }
  }

}
