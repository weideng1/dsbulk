/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.oss.dsbulk.executor.api.simulacron;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.dsbulk.executor.api.BulkExecutor;
import com.datastax.oss.dsbulk.executor.api.BulkExecutorITBase;
import com.datastax.oss.dsbulk.tests.simulacron.SimulacronExtension;
import com.datastax.oss.dsbulk.tests.simulacron.SimulacronUtils;
import com.datastax.oss.simulacron.common.result.SuccessResult;
import com.datastax.oss.simulacron.common.stubbing.PrimeDsl;
import com.datastax.oss.simulacron.server.BoundCluster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SimulacronExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BulkExecutorSimulacronITBase extends BulkExecutorITBase {

  private final BoundCluster simulacron;

  public BulkExecutorSimulacronITBase(
      BoundCluster simulacron, BulkExecutor failFastExecutor, BulkExecutor failSafeExecutor) {
    super(failFastExecutor, failSafeExecutor);
    this.simulacron = simulacron;
  }

  @BeforeEach
  void primeQueries() {
    SimulacronUtils.primeSystemLocal(simulacron, Collections.emptyMap());
    SimulacronUtils.primeSystemPeers(simulacron);
    SimulacronUtils.primeSystemPeersV2(simulacron);
    simulacron.prime(PrimeDsl.when(WRITE_QUERY).then(PrimeDsl.noRows()));
    simulacron.prime(PrimeDsl.when(READ_QUERY).then(createReadResult()));
    simulacron.prime(PrimeDsl.when(FAILED_QUERY).then(PrimeDsl.syntaxError("Bad Syntax")));
  }

  private static SuccessResult createReadResult() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      Map<String, Object> row = new HashMap<>();
      row.put("pk", i);
      row.put("v", i);
      rows.add(row);
    }
    Map<String, String> column_types = new HashMap<>();
    column_types.put("pk", "int");
    column_types.put("v", "int");
    return new SuccessResult(rows, column_types);
  }

  @Override
  protected void verifyWrites(int expected) {
    long size =
        simulacron.getLogs().getQueryLogs().stream()
            .filter(l -> l.getType().equals("QUERY"))
            .filter(l -> l.getQuery().contains("INSERT INTO test_write"))
            .count();
    assertThat(size).isEqualTo(expected);
  }
}