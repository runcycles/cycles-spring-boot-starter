package io.runcycles.demo.client.spring.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RepositoryAccessService {

    public Optional<String>findTenant (){
        return Optional.of("ecosystem-saulius-1");
    }
}
