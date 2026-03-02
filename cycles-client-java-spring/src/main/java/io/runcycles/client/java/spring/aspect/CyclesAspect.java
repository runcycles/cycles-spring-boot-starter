package io.runcycles.client.java.spring.aspect;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
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
import java.util.UUID;

@Aspect
public class CyclesAspect {
    private static final Logger LOG = LoggerFactory.getLogger(CyclesAspect.class);

    private final CyclesClient client;
    private final CommitRetryEngine retryEngine;
    private final CyclesExpressionEvaluator evaluator;

    public CyclesAspect(CyclesClient client,
                        CommitRetryEngine retryEngine,
                        CyclesExpressionEvaluator evaluator) {
        this.client = client;
        this.retryEngine = retryEngine;
        this.evaluator = evaluator;
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

        Map<String, Object> createBody = Map.of(
                "idempotency_key", UUID.randomUUID().toString(),
                "subject", Map.of(
                        "tenant", cycles.tenant(),
                        "workspace", cycles.workspace(),
                        "app", cycles.app()
                ),
                "action", Map.of(
                        "kind", cycles.actionKind(),
                        "name", cycles.actionName()
                ),
                "estimate", Map.of(
                        "unit", cycles.unit(),
                        "amount", estimate
                ),
                "ttl_ms", cycles.ttlMs()
        );
        LOG.info("Creating reservation: createBody={}",createBody);
        long resT1 = System.currentTimeMillis();
        String reservationId = client.createReservation(createBody);
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

            Map<String, Object> commitBody = Map.of(
                    "idempotency_key", UUID.randomUUID().toString(),
                    "actual", Map.of(
                            "unit", cycles.unit(),
                            "amount", actual
                    ),
                    "overage_policy", cycles.overagePolicy()
            );

            try {
                LOG.info("Commiting reservation: reservationId={}, commitBody={}",reservationId,commitBody);
                long comT1 = System.currentTimeMillis();
                client.commitReservation(reservationId, commitBody);
                long comT2 = System.currentTimeMillis();
                LOG.info("Commit done: elapseTime={}ms",(comT2-comT1));
            } catch (Exception e) {
                LOG.error("Failed to commit reservation: reservationId={}",reservationId,e);
                retryEngine.schedule(reservationId, commitBody);
            }
            long t2 = System.currentTimeMillis() ;
            LOG.info("Cycles aspect flow finished: elapseTime={}ms, cycles={}",(t2-t1), cycles);
            return result;

        } catch (Throwable ex) {
            LOG.error("Failed to process Cycles budget aspect: cycles={}", cycles,ex);
            try {
                LOG.info("Releasing reservation due to processing fault: reservationId={}",reservationId);
                client.releaseReservation(reservationId,
                        Map.of("idempotency_key", UUID.randomUUID().toString()));
            } catch (Exception ignored) {LOG.error("Failed to release reservation on main failure: reservationId={}",reservationId,ignored);}
            throw ex;
        } finally {
            CyclesContextHolder.clear();
        }
    }
}
