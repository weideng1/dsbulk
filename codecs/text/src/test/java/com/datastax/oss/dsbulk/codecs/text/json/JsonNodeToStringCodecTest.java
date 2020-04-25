/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.oss.dsbulk.codecs.text.json;

import static com.datastax.oss.dsbulk.tests.assertions.TestAssertions.assertThat;

import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.shaded.guava.common.collect.Lists;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonNodeToStringCodecTest {

  private List<String> nullStrings = Lists.newArrayList("NULL");
  private ObjectMapper objectMapper = JsonCodecUtils.getObjectMapper();
  private JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();

  @Test
  void should_convert_from_valid_external() throws IOException {
    JsonNodeToStringCodec codec =
        new JsonNodeToStringCodec(TypeCodecs.TEXT, objectMapper, nullStrings);
    assertThat(codec)
        .convertsFromExternal(nodeFactory.textNode("foo"))
        .toInternal("foo")
        .convertsFromExternal(nodeFactory.textNode("\"foo\""))
        .toInternal("\"foo\"")
        .convertsFromExternal(nodeFactory.textNode("'foo'"))
        .toInternal("'foo'")
        .convertsFromExternal(nodeFactory.numberNode(42))
        .toInternal("42")
        .convertsFromExternal(objectMapper.readTree("{\"foo\":42}"))
        .toInternal("{\"foo\":42}")
        .convertsFromExternal(objectMapper.readTree("[1,2,3]"))
        .toInternal("[1,2,3]")
        .convertsFromExternal(null)
        .toInternal(null)
        .convertsFromExternal(nodeFactory.textNode("NULL"))
        .toInternal(null)
        .convertsFromExternal(nodeFactory.textNode(""))
        .toInternal("") // empty string should nto be converted to null
        .convertsFromExternal(null)
        .toInternal(null);
  }

  @Test
  void should_convert_from_valid_internal() {
    JsonNodeToStringCodec codec =
        new JsonNodeToStringCodec(TypeCodecs.TEXT, objectMapper, nullStrings);
    assertThat(codec)
        .convertsFromInternal("foo")
        .toExternal(nodeFactory.textNode("foo"))
        .convertsFromInternal("\"foo\"")
        .toExternal(nodeFactory.textNode("\"foo\""))
        .convertsFromInternal("'foo'")
        .toExternal(nodeFactory.textNode("'foo'"))
        .convertsFromInternal("42")
        .toExternal(nodeFactory.textNode("42"))
        .convertsFromInternal("42 abc")
        .toExternal(nodeFactory.textNode("42 abc"))
        .convertsFromInternal("{\"foo\":42}")
        .toExternal(nodeFactory.textNode("{\"foo\":42}"))
        .convertsFromInternal("[1,2,3]")
        .toExternal(nodeFactory.textNode("[1,2,3]"))
        .convertsFromInternal("{\"foo\":42") // invalid json
        .toExternal(nodeFactory.textNode("{\"foo\":42"))
        .convertsFromInternal(null)
        .toExternal(null)
        .convertsFromInternal("")
        .toExternal(nodeFactory.textNode(""));
  }
}