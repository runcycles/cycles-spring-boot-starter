package io.runcycles.demo.client.spring.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Stub service simulating a database or external lookup for tenant resolution.
 *
 * In a real application, this would query a database, session, or external service
 * to determine the current tenant. The CyclesTenantResolver uses this to demonstrate
 * dynamic tenant resolution via the CyclesFieldResolver interface.
 *
 * Note: In this demo, cycles.tenant is set in application.yml, which takes priority
 * over the resolver. To see the resolver in action, remove cycles.tenant from the config.
 */
@Service
public class RepositoryAccessService {

    public Optional<String> findTenant() {
        return Optional.of("acme-corp");
    }
}
