/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.ddl.commands;

import com.google.common.annotations.VisibleForTesting;
import io.confluent.ksql.metastore.MetaStore;
import io.confluent.ksql.metastore.MutableMetaStore;
import io.confluent.ksql.metastore.model.DataSource;
import io.confluent.ksql.metastore.model.KsqlTopic;
import io.confluent.ksql.parser.tree.CreateSourceProperties;
import io.confluent.ksql.parser.tree.RegisterTopic;
import io.confluent.ksql.serde.KsqlSerdeFactories;
import io.confluent.ksql.serde.KsqlSerdeFactory;
import io.confluent.ksql.serde.SerdeFactories;
import io.confluent.ksql.util.KsqlException;

public class RegisterTopicCommand implements DdlCommand {

  private final String topicName;
  private final String kafkaTopicName;
  private final KsqlSerdeFactory valueSerdeFactory;
  private final boolean notExists;

  public RegisterTopicCommand(final RegisterTopic registerTopic) {
    this(registerTopic.getName().getSuffix(),
         registerTopic.isNotExists(),
         new CreateSourceProperties(registerTopic.getProperties())
    );
  }

  RegisterTopicCommand(
      final String topicName,
      final boolean notExist,
      final CreateSourceProperties properties
  ) {
    this(topicName, notExist, properties, new KsqlSerdeFactories());
  }

  @VisibleForTesting
  RegisterTopicCommand(
      final String topicName,
      final boolean notExist,
      final CreateSourceProperties properties,
      final SerdeFactories serdeFactories
  ) {
    this.topicName = topicName;
    this.kafkaTopicName = properties.getKafkaTopic();
    this.valueSerdeFactory = serdeFactories.create(properties.getValueFormat(), properties);
    this.notExists = notExist;
  }

  @Override
  public DdlCommandResult run(final MutableMetaStore metaStore) {
    if (metaStore.getTopic(topicName) != null) {
      // Check IF NOT EXIST is set, if set, do not create topic if one exists.
      if (notExists) {
        return new DdlCommandResult(true, "Topic already registered.");
      } else {
        final String sourceType = getSourceType(metaStore);
        final String errorMessage =
            String.format("%s with name '%s' already exists", sourceType, topicName);
        throw new KsqlException(errorMessage);
      }
    }

    final KsqlTopic ksqlTopic = new KsqlTopic(topicName, kafkaTopicName, valueSerdeFactory, false);

    metaStore.putTopic(ksqlTopic);

    return new DdlCommandResult(true, "Topic registered");
  }

  private String getSourceType(final MetaStore metaStore) {
    final DataSource<?> source = metaStore.getSource(topicName);
    if (source == null) {
      return "A topic";
    }

    switch (source.getDataSourceType()) {
      case KSTREAM:
        return "A stream";

      case KTABLE:
        return "A table";

      default:
        return "An entity";
    }
  }
}
