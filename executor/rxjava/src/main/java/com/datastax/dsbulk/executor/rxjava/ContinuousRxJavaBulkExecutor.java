/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.executor.rxjava;

import com.datastax.dsbulk.executor.api.BulkExecutor;
import com.datastax.dsbulk.executor.api.internal.publisher.ContinuousReadResultPublisher;
import com.datastax.dsbulk.executor.api.result.ReadResult;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Statement;
import io.reactivex.Flowable;
import java.util.Objects;

/**
 * An implementation of {@link BulkExecutor} using <a href="https://projectrxJava.io">RxJava</a>,
 * that executes all reads using continuous paging. This executor can achieve significant
 * performance improvements for reads, provided that the read statements to be executed can be
 * properly routed to a replica.
 */
public class ContinuousRxJavaBulkExecutor extends DefaultRxJavaBulkExecutor
    implements RxJavaBulkExecutor {

  /**
   * Creates a new builder for {@link ContinuousRxJavaBulkExecutor} instances using the given {@link
   * CqlSession}.
   *
   * @param session the {@link CqlSession} to use.
   * @return a new builder.
   */
  public static ContinuousRxJavaBulkExecutorBuilder continuousPagingBuilder(CqlSession session) {
    return new ContinuousRxJavaBulkExecutorBuilder(session);
  }

  private final CqlSession cqlSession;

  /**
   * Creates a new instance using the given {@link CqlSession} and using defaults for all
   * parameters.
   *
   * <p>If you need to customize your executor, use the {@link #continuousPagingBuilder(CqlSession)
   * builder} method instead.
   *
   * @param cqlSession the {@link CqlSession} to use.
   */
  public ContinuousRxJavaBulkExecutor(CqlSession cqlSession) {
    super(cqlSession);
    this.cqlSession = cqlSession;
  }

  ContinuousRxJavaBulkExecutor(ContinuousRxJavaBulkExecutorBuilder builder) {
    super(builder);
    this.cqlSession = builder.cqlSession;
  }

  @Override
  public Flowable<ReadResult> readReactive(Statement<?> statement) {
    Objects.requireNonNull(statement);
    return Flowable.fromPublisher(
        new ContinuousReadResultPublisher(
            statement,
            cqlSession,
            failFast,
            listener,
            maxConcurrentRequests,
            maxConcurrentQueries,
            rateLimiter));
  }
}
