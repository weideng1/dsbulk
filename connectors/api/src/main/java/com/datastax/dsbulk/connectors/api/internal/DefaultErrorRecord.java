/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.connectors.api.internal;

import com.datastax.dsbulk.connectors.api.ErrorRecord;
import com.datastax.dsbulk.connectors.api.Field;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/** A record that could not be fully parsed from its source. */
public class DefaultErrorRecord implements ErrorRecord {

  private final @NotNull Object source;
  private final @NotNull Supplier<URI> resource;
  private final long position;
  private final @NotNull Throwable error;

  /**
   * Creates a new error record.
   *
   * @param source the record's source.
   * @param resource the record's resource.
   * @param position the record's position.
   * @param error the error.
   */
  public DefaultErrorRecord(
      @NotNull Object source, @NotNull URI resource, long position, @NotNull Throwable error) {
    this(source, () -> resource, position, error);
  }

  /**
   * Creates a new error record.
   *
   * @param source the record's source.
   * @param resource the record's resource; will be memoized.
   * @param position the record's position.
   * @param error the error.
   */
  public DefaultErrorRecord(
      @NotNull Object source,
      @NotNull Supplier<URI> resource,
      long position,
      @NotNull Throwable error) {
    this.source = source;
    this.resource = Suppliers.memoize(resource::get);
    this.position = position;
    this.error = error;
  }

  @NotNull
  @Override
  public Object getSource() {
    return source;
  }

  @NotNull
  @Override
  public URI getResource() {
    return resource.get();
  }

  @Override
  public long getPosition() {
    return position;
  }

  @NotNull
  @Override
  public Set<Field> fields() {
    return ImmutableSet.of();
  }

  @NotNull
  @Override
  public Collection<Object> values() {
    return ImmutableList.of();
  }

  @Override
  public Object getFieldValue(@NotNull Field field) {
    return null;
  }

  @Override
  public void clear() {
    // NO-OP
  }

  @NotNull
  @Override
  public Throwable getError() {
    return error;
  }
}
