package io.runcycles.client.java.spring.aspect;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.context.CyclesRequestBuilderService;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
import io.runcycles.client.java.spring.model.CyclesProtocolException;
import io.runcycles.client.java.spring.model.CyclesResponse;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;

import io.runcycles.client.java.spring.context.CyclesContextHolder;
import io.runcycles.client.java.spring.context.CyclesReservationContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;

@Aspect
public class CyclesAspect {
    private static final Logger LOG = LoggerFactory.getLogger(CyclesAspect.class);


    private final CyclesClient client;
    private final CommitRetryEngine retryEngine;
    private final CyclesExpressionEvaluator evaluator;
    private final CyclesRequestBuilderService cyclesRequestBuilderService;
    private final CyclesProperties cyclesConfiguration;

    public CyclesAspect(CyclesClient client,
                        CommitRetryEngine retryEngine,
                        CyclesRequestBuilderService cyclesRequestBuilderService,
                        CyclesExpressionEvaluator evaluator,
                        CyclesProperties cyclesConfiguration) {
        this.client = client;
        this.retryEngine = retryEngine;
        this.cyclesRequestBuilderService = cyclesRequestBuilderService;
        this.evaluator = evaluator;
        this.cyclesConfiguration = cyclesConfiguration;
    }

    @Around("@annotation(cycles)")
    public Object around(ProceedingJoinPoint pjp, Cycles cycles) throws Throwable {
        LOG.info("Cycles aspect flow start: cycles={}", cycles);
        long t1 = System.currentTimeMillis() ;
        if (CyclesContextHolder.get() != null) {
            LOG.error("Nested annotation usage not supported");
            throw new IllegalStateException("Nested @Cycles not supported");
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        long estimate = evaluator.evaluate(
                cycles.estimateExpression(),
                method,
                pjp.getArgs(),
                null,
                pjp.getTarget()
        );
        LOG.info("Estimated usage: estimate={}",estimate);

        Map<String, Object> createBody = buildReservationRequest(cycles,estimate);

        LOG.info("Creating reservation: createBody={}",createBody);
        long resT1 = System.currentTimeMillis();
        CyclesResponse<Map<String,Object>> reservationResponse = client.createReservation(createBody);
        if (!reservationResponse.is2xx()){
            LOG.error("Reservation failed, aborting further processing: reservationResponse={}",reservationResponse);
            throw new CyclesProtocolException("Failed to proceed reservation: "+reservationResponse.getErrorMessage());
        }
        String reservationId = extractReservationId (reservationResponse);
        if (reservationId == null){
            LOG.error("Reservation was successful, but reservation id not found in the response body: reservationResponseBody={}",reservationResponse.getBody());
            throw new CyclesProtocolException("Failed to proceed reservation because of missing reservation identifier");
        }
        long resT2 = System.currentTimeMillis();
        LOG.info("Reservation created: elapseTime={}ms, reservationId={}",(resT2-resT1),reservationId);
        CyclesContextHolder.set(new CyclesReservationContext(reservationId, estimate));

        try {
            Object result = pjp.proceed();
            LOG.info("Annotated method finished its execution: reservationId={}, result={}",reservationId,result);
            long actual;
            if (!cycles.actualExpression().isBlank()) {
                actual = evaluator.evaluate(
                        cycles.actualExpression(),
                        method,
                        pjp.getArgs(),
                        result,
                        pjp.getTarget()
                );
            } else if (cycles.useEstimatedIfActualNotProvided()) {
                actual = estimate;
            } else {
                LOG.error("Actual usage amount is missing that is required");
                throw new IllegalStateException("Actual expression required");
            }

            Map<String,Object>commitBody = cyclesRequestBuilderService.buildCommit(cycles,actual);

            try {
                LOG.info("Commiting reservation: reservationId={}, commitBody={}",reservationId,commitBody);
                long comT1 = System.currentTimeMillis();
                CyclesResponse<Map<String,Object>> commitResponse = client.commitReservation(reservationId, commitBody);
                long comT2 = System.currentTimeMillis();
                LOG.info("Commit done: elapseTime={}ms, response={}",(comT2-comT1),commitResponse);
                if (commitResponse.is2xx()){
                    LOG.info("Commit was successful: reservationId={}, responseBody={}",reservationId,commitResponse.getBody());
                }
                else {
                    LOG.error("Commit failed: reservationId={}, reason={}, responseBody={}",reservationId,commitResponse.getErrorMessage(),commitResponse.getBody());
                    //FIXME need to check when should schedule retry and when not
                    if (commitResponse.isTransportError() || commitResponse.is5xx()){
                        retryEngine.schedule(reservationId, commitBody);
                    } else if (commitResponse.is4xx()) {
                        handleReleaseReservation(reservationId);
                    }
                    else {
                        LOG.warn("Unrecognized response so nothing to do: response={}",commitResponse);
                    }
                }

            } catch (Exception e) {
                LOG.error("Failed to commit reservation: reservationId={}",reservationId,e);
                retryEngine.schedule(reservationId, commitBody);
            }
            long t2 = System.currentTimeMillis() ;
            LOG.info("Cycles aspect flow finished: elapseTime={}ms, cycles={}",(t2-t1), cycles);
            return result;

        } catch (Throwable ex) {
            LOG.error("Guarded method execution failed, releasing reservation: reservationId={}, cycles={}", reservationId, cycles, ex);
            handleReleaseReservation(reservationId);
            throw ex;
        } finally {
            CyclesContextHolder.clear();
        }
    }
    private Map<String,Object>buildReservationRequest (Cycles cycles, long estimatedAmount){
        return cyclesRequestBuilderService.buildReservation(cycles,estimatedAmount);
    }
    private String extractReservationId (CyclesResponse<Map<String,Object>> response){
        return response.getBodyAttributeAsString("reservation_id") ;
    }
    private void handleReleaseReservation (String reservationId){
        try {
            LOG.info("Releasing reservation due to processing fault: reservationId={}",reservationId);
            CyclesResponse<Map<String,Object>> releaseResponse = client.releaseReservation(reservationId,
                    cyclesRequestBuilderService.buildRelease());
            LOG.info("Reservation released: reservationId={}, releaseResponse={}",reservationId,releaseResponse);
            if (releaseResponse.is2xx()){
                LOG.info("Reservation released successfully: reservationId={}, responseBody={}",reservationId,releaseResponse.getBody());
            }
            else {
                LOG.warn("Reservation release failed or is unknown: reservationId={}, errorMessage={}, responseBody={}",reservationId,releaseResponse.getErrorMessage(),releaseResponse.getBody());
            }
        } catch (Exception ignored) {LOG.error("Failed to release reservation on main failure: reservationId={}",reservationId,ignored);}
    }
}
