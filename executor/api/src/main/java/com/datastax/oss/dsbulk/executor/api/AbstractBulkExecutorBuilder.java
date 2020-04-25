/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.oss.dsbulk.executor.api;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.dsbulk.executor.api.exception.BulkExecutionException;
import com.datastax.oss.dsbulk.executor.api.listener.ExecutionListener;
import com.datastax.oss.dsbulk.executor.api.result.ReadResult;
import com.datastax.oss.dsbulk.executor.api.result.Result;
import com.datastax.oss.dsbulk.executor.api.result.WriteResult;

/** A builder for {@link AbstractBulkExecutor} instances. */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractBulkExecutorBuilder<T extends AbstractBulkExecutor> {

  protected final CqlSession session;

  protected boolean failFast = true;

  protected int maxInFlightRequests = AbstractBulkExecutor.DEFAULT_MAX_IN_FLIGHT_REQUESTS;

  protected int maxInFlightQueries = AbstractBulkExecutor.DEFAULT_MAX_IN_FLIGHT_QUERIES;

  protected int maxRequestsPerSecond = AbstractBulkExecutor.DEFAULT_MAX_REQUESTS_PER_SECOND;

  protected ExecutionListener listener;

  protected AbstractBulkExecutorBuilder(CqlSession session) {
    this.session = session;
  }

  /**
   * Switches on fail-safe mode.
   *
   * <p>By default, executors are created in fail-fast mode, i.e. any execution error stops the
   * whole operation. In fail-fast mode, the error is wrapped within a {@link
   * BulkExecutionException} that conveys the failed {@link Statement}. With synchronous operations,
   * the exception is thrown directly; with asynchronous ones, the returned future is completed
   * exceptionally. <b>Important</b>: in fail-fast mode, if a statement execution fails, all pending
   * requests are abandoned; there is no guarantee that all previously submitted statements will
   * complete before the executor stops.
   *
   * <p>In fail-safe mode, the error is converted into a {@link WriteResult} or {@link ReadResult}
   * and passed on to consumers, along with the failed {@link Statement}; then the execution resumes
   * at the next statement. The {@link Result} interface exposes two useful methods when operating
   * in fail-safe mode:
   *
   * <ol>
   *   <li>{@link Result#isSuccess()} tells if the statement was executed successfully.
   *   <li>{@link Result#getError()} can be used to retrieve the error.
   * </ol>
   *
   * @return this builder (for method chaining).
   */
  @SuppressWarnings("UnusedReturnValue")
  public AbstractBulkExecutorBuilder<T> failSafe() {
    this.failFast = false;
    return this;
  }

  /**
   * Sets the maximum number of in-flight requests. In other words, sets the maximum number of
   * concurrent uncompleted requests waiting for a response from the server. If that limit is
   * reached, the executor will block until the number of in-flight requests drops below the
   * threshold. <em>This feature should not be used in a fully non-blocking application</em>.
   *
   * <p>This acts as a safeguard against workflows that generate more requests than they can handle.
   * The default is {@link AbstractBulkExecutor#DEFAULT_MAX_IN_FLIGHT_REQUESTS}. Setting this option
   * to any negative value will disable it.
   *
   * <p>This method acts on request level; its equivalent on the query level is {@link
   * #withMaxInFlightQueries(int)}.
   *
   * @param maxInFlightRequests the maximum number of in-flight requests.
   * @return this builder (for method chaining).
   */
  @SuppressWarnings("UnusedReturnValue")
  public AbstractBulkExecutorBuilder<T> withMaxInFlightRequests(int maxInFlightRequests) {
    this.maxInFlightRequests = maxInFlightRequests;
    return this;
  }

  /**
   * Sets the maximum number of in-flight queries. In other words, sets the maximum number of
   * concurrent uncompleted queries waiting for completion. If that limit is reached, the executor
   * will block until the number of in-flight queries drops below the threshold. <em>This feature
   * should not be used in a fully non-blocking application</em>.
   *
   * <p>This acts as a safeguard against workflows that generate more queries than they can handle.
   * The default is {@link AbstractBulkExecutor#DEFAULT_MAX_IN_FLIGHT_QUERIES}. Setting this option
   * to any negative value will disable it.
   *
   * <p>This method acts on query level; its equivalent on the request level is {@link
   * #withMaxInFlightRequests(int)}.
   *
   * @param maxInFlightQueries the maximum number of in-flight queries.
   * @return this builder (for method chaining).
   */
  @SuppressWarnings("UnusedReturnValue")
  public AbstractBulkExecutorBuilder<T> withMaxInFlightQueries(int maxInFlightQueries) {
    this.maxInFlightQueries = maxInFlightQueries;
    return this;
  }

  /**
   * Sets the maximum number of concurrent requests per second. If that limit is reached, the
   * executor will block until the number of requests per second drops below the threshold. <em>This
   * feature should not be used in a fully non-blocking application</em>.
   *
   * <p>This acts as a safeguard against workflows that could overwhelm the cluster with more
   * requests than it can handle. The default is {@link
   * AbstractBulkExecutor#DEFAULT_MAX_REQUESTS_PER_SECOND}. Setting this option to any negative
   * value will disable it.
   *
   * @param maxRequestsPerSecond the maximum number of concurrent requests per second.
   * @return this builder (for method chaining).
   */
  @SuppressWarnings("UnusedReturnValue")
  public AbstractBulkExecutorBuilder<T> withMaxRequestsPerSecond(int maxRequestsPerSecond) {
    this.maxRequestsPerSecond = maxRequestsPerSecond;
    return this;
  }

  /**
   * Sets an optional {@link ExecutionListener}.
   *
   * @param listener the {@link ExecutionListener} to use.
   * @return this builder (for method chaining).
   */
  @SuppressWarnings("UnusedReturnValue")
  public AbstractBulkExecutorBuilder<T> withExecutionListener(ExecutionListener listener) {
    this.listener = listener;
    return this;
  }

  /**
   * Builds a new instance.
   *
   * @return the newly-created instance.
   */
  public abstract T build();
}