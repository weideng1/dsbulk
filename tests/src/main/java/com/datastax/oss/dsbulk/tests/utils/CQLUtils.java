/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.oss.dsbulk.tests.utils;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;

public abstract class CQLUtils {

  private static final String CREATE_KEYSPACE_SIMPLE_FORMAT =
      "CREATE KEYSPACE %s WITH replication = { 'class' : 'SimpleStrategy', 'replication_factor' : %d }";

  public static SimpleStatement createKeyspaceSimpleStrategy(
      String keyspace, int replicationFactor) {
    return createKeyspaceSimpleStrategy(CqlIdentifier.fromInternal(keyspace), replicationFactor);
  }

  public static SimpleStatement createKeyspaceSimpleStrategy(
      CqlIdentifier keyspace, int replicationFactor) {
    return SimpleStatement.newInstance(
        String.format(CREATE_KEYSPACE_SIMPLE_FORMAT, keyspace.asCql(true), replicationFactor));
  }

  public static SimpleStatement createKeyspaceNetworkTopologyStrategy(
      String keyspace, int... replicationFactors) {
    return createKeyspaceNetworkTopologyStrategy(
        CqlIdentifier.fromInternal(keyspace), replicationFactors);
  }

  public static SimpleStatement createKeyspaceNetworkTopologyStrategy(
      CqlIdentifier keyspace, int... replicationFactors) {
    StringBuilder sb =
        new StringBuilder("CREATE KEYSPACE ")
            .append(keyspace.asCql(true))
            .append(" WITH replication = { 'class' : 'NetworkTopologyStrategy', ");
    for (int i = 0; i < replicationFactors.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      int rf = replicationFactors[i];
      sb.append("'dc").append(i + 1).append("' : ").append(rf);
    }
    return SimpleStatement.newInstance(sb.append('}').toString());
  }

  public static SimpleStatement truncateTable(String keyspace, String table) {
    return SimpleStatement.newInstance(
        "TRUNCATE "
            + CqlIdentifier.fromInternal(keyspace).asCql(true)
            + "."
            + CqlIdentifier.fromInternal(table).asCql(true));
  }
}