package io.runcycles.demo.client.spring.resolvers;

import io.runcycles.client.java.spring.evaluation.CyclesFieldResolver;
import io.runcycles.demo.client.spring.service.RepositoryAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("tenant")
public class CyclesTenantResolver implements CyclesFieldResolver {

    private static final Logger LOG = LoggerFactory.getLogger(CyclesTenantResolver.class);

    @Autowired
    private RepositoryAccessService repositoryAccessService;

    @Override
    public String resolve() {
        LOG.info("Resolving tenant via Cycles field resolver interface...");
        Optional<String> tenantOpt = repositoryAccessService.findTenant();
        return tenantOpt.orElse(null);
    }
}
