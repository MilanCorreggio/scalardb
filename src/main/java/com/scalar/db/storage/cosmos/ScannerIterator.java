package com.scalar.db.storage.cosmos;

import com.scalar.db.api.Result;
import com.scalar.db.api.Selection;
import java.util.Iterator;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public final class ScannerIterator implements Iterator<Result> {
  private final Iterator<Record> iterator;
  private final Selection selection;
  private final CosmosTableMetadata metadata;

  public ScannerIterator(Iterator<Record> iterator, Selection selection, CosmosTableMetadata metadata) {
    this.iterator = iterator;
    this.selection = selection;
    this.metadata = metadata;
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  @Nullable
  public Result next() {
    Record record = iterator.next();
    if (record == null) {
      return null;
    }

    return new ResultImpl(record, selection, metadata);
  }
}
