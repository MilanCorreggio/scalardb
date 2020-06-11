package com.scalar.db.storage.cosmos;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientException;
import com.azure.cosmos.CosmosStoredProcedure;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosStoredProcedureRequestOptions;
import com.scalar.db.api.Mutation;
import com.scalar.db.api.Delete;
import com.scalar.db.api.Put;
import com.scalar.db.api.Operation;
import com.scalar.db.io.Value;
import java.util.Optional;
import com.azure.cosmos.models.PartitionKey;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An abstraction for handler classes for mutate statements */
@ThreadSafe
public abstract class MutateStatementHandler extends StatementHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(MutateStatementHandler.class);
  private final String DELETE_IF = "deleteIf.js";

  public MutateStatementHandler(CosmosClient client, TableMetadataHandler metadataHandler) {
    super(client, metadataHandler);
  }

  protected void executeStoredProcedure(String storedProcedureName, Mutation mutation)
      throws CosmosClientException {
    Optional<Record> record = makeRecord(mutation);
    String query = makeConditionalQuery(mutation);
    Object[] args = record.isPresent() ? new Object[] {record, query} : new Object[] {query};

    CosmosStoredProcedureRequestOptions options =
        new CosmosStoredProcedureRequestOptions()
            .setConsistencyLevel(convert(mutation))
            .setPartitionKey(new PartitionKey(getConcatPartitionKey(mutation)));

    CosmosStoredProcedure storedProcedure =
        getContainer(mutation).getScripts().getStoredProcedure(storedProcedureName);
    storedProcedure.execute(args, options);
  }

  /**
   * Returns a {@link CosmosItemRequestOptions} with the consistency level for the specified {@link
   * Operation}
   *
   * @param operation an {@code Operation}
   */
  protected ConsistencyLevel convert(Operation operation) {
    switch (operation.getConsistency()) {
      case SEQUENTIAL:
        return ConsistencyLevel.STRONG;
      case EVENTUAL:
        return ConsistencyLevel.EVENTUAL;
      case LINEARIZABLE:
        return ConsistencyLevel.STRONG;
      default:
        LOGGER.warn("Unsupported consistency is specified. SEQUENTIAL is being used instead.");
        return ConsistencyLevel.STRONG;
    }
  }

  protected Optional<Record> makeRecord(Mutation mutation) {
    if (mutation instanceof Delete) {
      return Optional.empty();
    }
    checkArgument(mutation, Put.class);
    Put put = (Put) mutation;

    Record record = new Record();
    record.setId(getId(put));
    record.setConcatPartitionKey(getConcatPartitionKey(put));

    MapVisitor partitionKeyVisitor = new MapVisitor();
    for (Value v : put.getPartitionKey()) {
      v.accept(partitionKeyVisitor);
    }
    record.setPartitionKey(partitionKeyVisitor.get());

    put.getClusteringKey()
        .ifPresent(
            k -> {
              MapVisitor clusteringKeyVisitor = new MapVisitor();
              k.get()
                  .forEach(
                      v -> {
                        v.accept(clusteringKeyVisitor);
                      });
              record.setClusteringKey(clusteringKeyVisitor.get());
            });

    MapVisitor visitor = new MapVisitor();
    put.getValues()
        .values()
        .forEach(
            v -> {
              v.accept(visitor);
            });
    record.setValues(visitor.get());

    return Optional.of(record);
  }

  protected String makeConditionalQuery(Mutation mutation) {
    String concatPartitionKey = getConcatPartitionKey(mutation);
    ConditionQueryBuilder builder = new ConditionQueryBuilder(concatPartitionKey);

    mutation
        .getClusteringKey()
        .ifPresent(
            k -> {
              builder.withClusteringKey(k);
            });

    mutation
        .getCondition()
        .ifPresent(
            c -> {
              c.accept(builder);
            });

    return builder.build();
  }
}
