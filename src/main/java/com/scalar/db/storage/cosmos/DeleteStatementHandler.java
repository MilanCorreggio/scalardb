package com.scalar.db.storage.cosmos;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.scalar.db.api.Delete;
import com.scalar.db.api.Operation;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class DeleteStatementHandler extends MutateStatementHandler {
  private final String DELETE_IF = "deleteIf.js";

  public DeleteStatementHandler(CosmosClient client, TableMetadataHandler metadataHandler) {
    super(client, metadataHandler);
  }

  @Override
  protected List<Record> execute(Operation operation) throws CosmosClientException {
    checkArgument(operation, Delete.class);
    Delete delete = (Delete) operation;

    if (delete.getCondition().isPresent()) {
      executeStoredProcedure(DELETE_IF, delete);
    } else {
      executeDeletion(delete);
    }

    return Collections.emptyList();
  }

  private void executeDeletion(Delete delete) throws CosmosClientException {
    String id = getId(delete);
    PartitionKey partitionKey = new PartitionKey(getConcatPartitionKey(delete));
    CosmosItemRequestOptions options =
        new CosmosItemRequestOptions().setConsistencyLevel(convert(delete));

    getContainer(delete).deleteItem(id, partitionKey, options);
  }
}
