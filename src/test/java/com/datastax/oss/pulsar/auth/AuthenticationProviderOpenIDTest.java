/*
 * Copyright DataStax, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.pulsar.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.DefaultJwtBuilder;
import io.jsonwebtoken.security.Keys;
import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.authentication.AuthenticationDataCommand;
import org.junit.jupiter.api.*;

import javax.naming.AuthenticationException;
import java.security.KeyPair;
import java.sql.Date;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Tests to cover the AuthenticationProviderOpenID
 *
 * This class only tests the verification of tokens. It does not test the integration to retrieve tokens
 * from an identity provider.
 *
 * Note: this class uses the io.jsonwebtoken library here because it has more utilities than the auth0 library.
 * The jsonwebtoken library makes it easy to generate key pairs for many algorithms and it also has an enum
 * that can be used to assert that unsupported algorithms properly fail validation.
 */
public class AuthenticationProviderOpenIDTest {

    @Test
    public void testNullToken() {
        AuthenticationProviderOpenID provider = new AuthenticationProviderOpenID();
        Assertions.assertThrows(AuthenticationException.class,
                () -> provider.authenticate(new AuthenticationDataCommand(null)));
    }

    @Test
    public void ensureEmptyIssuersFailsInitialization() {
        AuthenticationProviderOpenID provider = new AuthenticationProviderOpenID();
        Properties props = new Properties();
        props.setProperty(AuthenticationProviderOpenID.ALLOWED_TOKEN_ISSUERS, "");
        ServiceConfiguration config = new ServiceConfiguration();
        config.setProperties(props);
        Assertions.assertThrows(IllegalArgumentException.class, () -> provider.initialize(config));
    }

    @Test
    public void ensureInsecureIssuerFailsInitialization() {
        AuthenticationProviderOpenID provider = new AuthenticationProviderOpenID();
        Properties props = new Properties();
        props.setProperty(AuthenticationProviderOpenID.ALLOWED_TOKEN_ISSUERS, "https://myissuer.com,http://myissuer.com");
        ServiceConfiguration config = new ServiceConfiguration();
        config.setProperties(props);
        Assertions.assertThrows(IllegalArgumentException.class, () -> provider.initialize(config));
    }

    @Test void ensureMissingRoleClaimReturnsNull() throws Exception {
        AuthenticationProviderOpenID provider = new AuthenticationProviderOpenID();
        Properties props = new Properties();
        props.setProperty(AuthenticationProviderOpenID.ALLOWED_TOKEN_ISSUERS, "https://myissuer.com");
        props.setProperty(AuthenticationProviderOpenID.ATTEMPT_AUTHENTICATION_PROVIDER_TOKEN, "false");
        props.setProperty(AuthenticationProviderOpenID.ROLE_CLAIM, "sub");
        ServiceConfiguration config = new ServiceConfiguration();
        config.setProperties(props);
        provider.initialize(config);

        // Build an empty JWT
        DefaultJwtBuilder defaultJwtBuilder = new DefaultJwtBuilder();
        defaultJwtBuilder.setAudience("audience");
        DecodedJWT jwtWithoutSub = JWT.decode(defaultJwtBuilder.compact());

        // A JWT with an empty role claim must result in a null role
        Assertions.assertNull(provider.getRole(jwtWithoutSub));
    }

    @Test void ensureRoleClaimForStringReturnsRole() throws Exception {
        AuthenticationProviderOpenID provider = new AuthenticationProviderOpenID();
        Properties props = new Properties();
        props.setProperty(AuthenticationProviderOpenID.ALLOWED_TOKEN_ISSUERS, "https://myissuer.com");
        props.setProperty(AuthenticationProviderOpenID.ATTEMPT_AUTHENTICATION_PROVIDER_TOKEN, "false");
        props.setProperty(AuthenticationProviderOpenID.ROLE_CLAIM, "sub");
        ServiceConfiguration config = new ServiceConfiguration();
        config.setProperties(props);
        provider.initialize(config);

        // Build an empty JWT
        DefaultJwtBuilder defaultJwtBuilder = new DefaultJwtBuilder();
        defaultJwtBuilder.setSubject("my-role");
        DecodedJWT jwt = JWT.decode(defaultJwtBuilder.compact());

        Assertions.assertEquals("my-role", provider.getRole(jwt));
    }

    @Test void ensureRoleClaimForSingletonListReturnsRole() throws Exception {
        AuthenticationProviderOpenID provider = new AuthenticationProviderOpenID();
        Properties props = new Properties();
        props.setProperty(AuthenticationProviderOpenID.ALLOWED_TOKEN_ISSUERS, "https://myissuer.com");
        props.setProperty(AuthenticationProviderOpenID.ATTEMPT_AUTHENTICATION_PROVIDER_TOKEN, "false");
        props.setProperty(AuthenticationProviderOpenID.ROLE_CLAIM, "roles");
        ServiceConfiguration config = new ServiceConfiguration();
        config.setProperties(props);
        provider.initialize(config);

        // Build an empty JWT
        DefaultJwtBuilder defaultJwtBuilder = new DefaultJwtBuilder();
        HashMap<String, List<String>> claims = new HashMap();
        claims.put("roles", Collections.singletonList("my-role"));
        defaultJwtBuilder.setClaims(claims);
        DecodedJWT jwt = JWT.decode(defaultJwtBuilder.compact());

        Assertions.assertEquals("my-role", provider.getRole(jwt));
    }

    @Test void ensureRoleClaimForMultiEntryListReturnsFirstRole() throws Exception {
        AuthenticationProviderOpenID provider = new AuthenticationProviderOpenID();
        Properties props = new Properties();
        props.setProperty(AuthenticationProviderOpenID.ALLOWED_TOKEN_ISSUERS, "https://myissuer.com");
        props.setProperty(AuthenticationProviderOpenID.ATTEMPT_AUTHENTICATION_PROVIDER_TOKEN, "false");
        props.setProperty(AuthenticationProviderOpenID.ROLE_CLAIM, "roles");
        ServiceConfiguration config = new ServiceConfiguration();
        config.setProperties(props);
        provider.initialize(config);

        // Build an empty JWT
        DefaultJwtBuilder defaultJwtBuilder = new DefaultJwtBuilder();
        HashMap<String, List<String>> claims = new HashMap();
        claims.put("roles", Arrays.asList("my-role-1", "my-role-2"));
        defaultJwtBuilder.setClaims(claims);
        DecodedJWT jwt = JWT.decode(defaultJwtBuilder.compact());

        Assertions.assertEquals("my-role-1", provider.getRole(jwt));
    }

    @Test void ensureRoleClaimForEmptyListReturnsNull() throws Exception {
        AuthenticationProviderOpenID provider = new AuthenticationProviderOpenID();
        Properties props = new Properties();
        props.setProperty(AuthenticationProviderOpenID.ALLOWED_TOKEN_ISSUERS, "https://myissuer.com");
        props.setProperty(AuthenticationProviderOpenID.ATTEMPT_AUTHENTICATION_PROVIDER_TOKEN, "false");
        props.setProperty(AuthenticationProviderOpenID.ROLE_CLAIM, "roles");
        ServiceConfiguration config = new ServiceConfiguration();
        config.setProperties(props);
        provider.initialize(config);

        // Build an empty JWT
        DefaultJwtBuilder defaultJwtBuilder = new DefaultJwtBuilder();
        HashMap<String, List<String>> claims = new HashMap();
        claims.put("roles", Collections.emptyList());
        defaultJwtBuilder.setClaims(claims);
        DecodedJWT jwt = JWT.decode(defaultJwtBuilder.compact());

        // A JWT with an empty list role claim must result in a null role
        Assertions.assertNull(provider.getRole(jwt));
    }
}
