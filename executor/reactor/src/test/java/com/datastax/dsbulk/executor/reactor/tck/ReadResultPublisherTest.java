/*
 * Copyright DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dsbulk.executor.reactor.tck;

import com.datastax.dsbulk.executor.api.result.ReadResult;
import com.datastax.dsbulk.executor.api.tck.ReadResultPublisherTestBase;
import com.datastax.dsbulk.executor.reactor.DefaultReactorBulkExecutor;
import org.reactivestreams.Publisher;

public class ReadResultPublisherTest extends ReadResultPublisherTestBase {

  @Override
  public Publisher<ReadResult> createPublisher(long elements) {
    DefaultReactorBulkExecutor executor =
        new DefaultReactorBulkExecutor(setUpSuccessfulSession(elements));
    return executor.readReactive("irrelevant");
  }

  @Override
  public Publisher<ReadResult> createFailedPublisher() {
    DefaultReactorBulkExecutor executor = new DefaultReactorBulkExecutor(setUpFailedSession());
    return executor.readReactive("irrelevant");
  }
}