/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.mysql.v1;

import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.DependencyLink;
import zipkin2.storage.StorageComponent;

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ITMySQLStorage {

  @RegisterExtension MySQLStorageExtension backend = new MySQLStorageExtension(
    "openzipkin/zipkin-mysql:2.15.0");

  @Nested
  class ITSpanStore extends zipkin2.storage.ITSpanStore<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.computeStorageBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }

    @Override @Test @Disabled("No consumer-side span deduplication") public void deduplicates() {
    }
  }

  @Nested
  class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.computeStorageBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.computeStorageBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITDependenciesPreAggregated extends zipkin2.storage.ITDependencies<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.computeStorageBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }

    /**
     * The current implementation does not include dependency aggregation. It includes retrieval of
     * pre-aggregated links, usually made via zipkin-dependencies
     */
    @Override protected void processDependencies(List<zipkin2.Span> spans) throws Exception {
      try (Connection conn = storage.datasource.getConnection()) {
        DSLContext context = storage.context.get(conn);

        // batch insert the rows at timestamp midnight
        List<Query> inserts = new ArrayList<>();
        aggregateLinks(spans).forEach((midnight, links) -> {
          Date day = new Date(midnight);
          for (DependencyLink link : links) {
            inserts.add(context.insertInto(ZIPKIN_DEPENDENCIES)
              .set(ZIPKIN_DEPENDENCIES.DAY, day)
              .set(ZIPKIN_DEPENDENCIES.PARENT, link.parent())
              .set(ZIPKIN_DEPENDENCIES.CHILD, link.child())
              .set(ZIPKIN_DEPENDENCIES.CALL_COUNT, link.callCount())
              .set(ZIPKIN_DEPENDENCIES.ERROR_COUNT, link.errorCount())
              .onDuplicateKeyIgnore());
          }
        });
        context.batch(inserts).execute();
      }
    }
  }

  @Nested
  class ITServiceAndSpanNames extends zipkin2.storage.ITServiceAndSpanNames<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.computeStorageBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.computeStorageBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITDependenciesOnDemand extends zipkin2.storage.ITDependencies<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.computeStorageBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }
}
