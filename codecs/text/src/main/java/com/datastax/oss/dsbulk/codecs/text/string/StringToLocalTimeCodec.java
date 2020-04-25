/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.oss.dsbulk.codecs.text.string;

import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.dsbulk.codecs.util.CodecUtils;
import com.datastax.oss.dsbulk.codecs.util.TemporalFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.List;

public class StringToLocalTimeCodec extends StringToTemporalCodec<LocalTime> {

  private final ZoneId timeZone;

  public StringToLocalTimeCodec(TemporalFormat parser, ZoneId timeZone, List<String> nullStrings) {
    super(TypeCodecs.TIME, parser, nullStrings);
    this.timeZone = timeZone;
  }

  @Override
  public LocalTime externalToInternal(String s) {
    TemporalAccessor temporal = parseTemporalAccessor(s);
    if (temporal == null) {
      return null;
    }
    return CodecUtils.toLocalTime(temporal, timeZone);
  }
}